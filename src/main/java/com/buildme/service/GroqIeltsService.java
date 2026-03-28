package com.buildme.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class GroqIeltsService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.transcription.url}")
    private String transcriptionUrl;

    @Value("${groq.chat.url}")
    private String chatUrl;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(60))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── STEP 1: Transcribe audio with Groq Whisper ─────────────
    private String transcribeAudio(MultipartFile audioFile) throws IOException {
        RequestBody fileBody = RequestBody.create(
                audioFile.getBytes(),
                MediaType.parse(audioFile.getContentType() != null
                        ? audioFile.getContentType() : "audio/webm")
        );

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getOriginalFilename() != null
                        ? audioFile.getOriginalFilename() : "audio.webm", fileBody)
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("language", "en")
                .addFormDataPart("response_format", "text")
                .build();

        Request request = new Request.Builder()
                .url(transcriptionUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Groq transcription error {}: {}", response.code(), body);
                throw new IOException("Groq transcription failed: " + response.code());
            }
            String transcript = body.trim();
            log.info("✅ Transcript: {}", transcript);
            return transcript.isEmpty() ? "[No speech detected]" : transcript;
        }
    }

    // ── STEP 2: Evaluate with Groq LLaMA ──────────────────────
    private Map<String, Object> evaluateWithLlama(
            String question, String transcript) throws IOException {

        String prompt = """
            You are an expert IELTS examiner. Evaluate the candidate's speaking response.

            IELTS QUESTION: %s
            CANDIDATE RESPONSE: %s

            Score using official IELTS band descriptors (1-9):
            - fluency: Fluency & Coherence
            - pronunciation: Pronunciation (infer from word choice and structure)
            - vocabulary: Lexical Resource
            - grammar: Grammatical Range & Accuracy
            - overallBand: Average of the four scores

            Respond ONLY with valid JSON, no explanation, no markdown:
            {
              "fluency": 6.5,
              "pronunciation": 6.0,
              "vocabulary": 6.5,
              "grammar": 6.0,
              "overallBand": 6.25,
              "feedback": "2-3 sentence overall feedback referencing their specific answer",
              "improvements": [
                "Specific improvement 1",
                "Specific improvement 2",
                "Specific improvement 3"
              ]
            }
            """.formatted(question, transcript);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "llama-3.3-70b-versatile",
                "temperature", 0.2,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are an IELTS examiner. Always respond with valid JSON only."),
                        Map.of("role", "user", "content", prompt)
                )
        ));

        Request request = new Request.Builder()
                .url(chatUrl)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Groq chat error {}: {}", response.code(), body);
                throw new IOException("Groq evaluation failed: " + response.code());
            }

            JsonNode root = objectMapper.readTree(body);
            String content = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();

            // Strip markdown fences if present
            String cleanJson = content.replaceAll("(?s)```json\\s*|```", "").trim();
            log.debug("LLaMA eval response: {}", cleanJson);

            return parseEvaluation(cleanJson, transcript);
        }
    }

    // ── PUBLIC: Transcribe + Evaluate in sequence ──────────────
    public Map<String, Object> transcribeAndEvaluate(
            MultipartFile audioFile, String question) throws IOException {

        // Step 1: Transcribe
        String transcript = transcribeAudio(audioFile);

        // Step 2: Evaluate
        return evaluateWithLlama(question, transcript);
    }

    // ── Parse LLaMA JSON response ──────────────────────────────
    private Map<String, Object> parseEvaluation(
            String json, String transcript) throws IOException {

        JsonNode node = objectMapper.readTree(json);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transcript",    transcript);
        result.put("fluency",       node.path("fluency").asDouble());
        result.put("pronunciation", node.path("pronunciation").asDouble());
        result.put("vocabulary",    node.path("vocabulary").asDouble());
        result.put("grammar",       node.path("grammar").asDouble());
        result.put("overallBand",   node.path("overallBand").asDouble());
        result.put("feedback",      node.path("feedback").asText());

        List<String> improvements = new ArrayList<>();
        node.path("improvements").forEach(i -> improvements.add(i.asText()));
        result.put("improvements", improvements);

        return result;
    }
}