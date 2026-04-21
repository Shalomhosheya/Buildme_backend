package com.buildme.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final RestTemplate restTemplate;

    @Value("${app.ai.endpoint:http://localhost:7860/api/predict}")
    private String aiEndpoint;

    @PostMapping("/message")
    public ResponseEntity<?> message(@RequestBody MessageRequest req, Authentication auth) {
        String reply;
        String type;

        // Detect intent
        String lower = req.message.toLowerCase();
        boolean isCorrection = lower.matches(".*\\b(fix|correct|wrong|grammar|mistake|error)\\b.*")
                || lower.matches(".*\\b(he|she|they|i|we)\\s+(go|is|are|was|have|don't|doesn't|didn't)\\b.*");
        boolean isIdeas      = lower.matches(".*\\b(idea|topic|argument|vocabulary|vocab|word|theme)\\b.*");

        // Try AI model
        String aiReply = callAiModel(req.message, req.history, isCorrection, isIdeas);
        if (aiReply != null) {
            reply = aiReply;
            type  = isCorrection ? "correction" : isIdeas ? "ideas" : "answer";
        } else {
            // Rule-based fallback
            if (isCorrection) { reply = buildCorrectionReply(req.message); type = "correction"; }
            else if (isIdeas) { reply = buildIdeasReply(req.message);      type = "ideas";      }
            else              { reply = buildAnswerReply(req.message);      type = "answer";     }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("reply", reply);
        resp.put("type",  type);
        return ResponseEntity.ok(resp);
    }

    // ── AI model call ──────────────────────────────────────────────────────
    private String callAiModel(String message, List<Map<String, String>> history, boolean correction, boolean ideas) {
        try {
            String systemPrompt = buildSystemPrompt(correction, ideas);
            StringBuilder historyText = new StringBuilder();
            if (history != null) {
                for (Map<String, String> h : history) {
                    String role = "user".equals(h.get("role")) ? "User" : "Assistant";
                    historyText.append(role).append(": ").append(h.get("text")).append("\n");
                }
            }
            String prompt = String.format("<|system|>\n%s<|end|>\n<|user|>\n%s%s<|end|>\n<|assistant|>",
                    systemPrompt, historyText, message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    aiEndpoint,
                    new HttpEntity<>(Map.of("data", List.of(prompt)), headers),
                    Map.class);

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                List<?> data = (List<?>) res.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    String text = data.get(0).toString().trim();
                    if (!text.isEmpty()) return text;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String buildSystemPrompt(boolean correction, boolean ideas) {
        if (correction) return """
                You are an IELTS English language expert. When given a sentence to fix:
                1. Show the corrected sentence in bold
                2. List each error with a brief explanation
                3. Explain the grammar rule
                4. Give a tip for IELTS writing
                Keep responses clear and educational. Use markdown formatting.""";
        if (ideas) return """
                You are an IELTS preparation expert. When asked for topic ideas:
                1. Give 3-4 main arguments for each side
                2. Provide 8-10 topic-specific vocabulary words with definitions
                3. Suggest an essay structure
                4. Give 2 example thesis statements
                Keep responses practical and IELTS-focused. Use markdown formatting.""";
        return """
                You are a knowledgeable IELTS assistant. Answer questions about:
                - IELTS exam format, band scores, criteria, and strategies
                - Writing, Speaking, Reading and Listening skills
                - Grammar and vocabulary for IELTS
                Be clear, helpful and practical. Use examples. Use markdown formatting.
                Keep responses concise (under 200 words unless detail is needed).""";
    }

    // ── Rule-based fallbacks ───────────────────────────────────────────────

    private String buildCorrectionReply(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("he go") || lower.contains("she go"))
            return """
                **Corrected sentence:** "He **goes** to school." / "She **went** to school yesterday."
                
                **Errors found:**
                • **"go" → "goes"** (present) or "went" (past)
                  — Third person singular (he/she/it) needs -s in present simple
                
                **Grammar rule:**
                Subject-verb agreement:
                • I/you/we/they → go
                • he/she/it → **goes**
                
                **IELTS tip:** Grammatical Range & Accuracy is 25% of your writing score. Consistent subject-verb errors will limit you to Band 5.""";

        if (lower.contains("don't knows") || lower.contains("doesn't know"))
            return """
                    **Corrected sentence:** "She **doesn't know** the answer."
                    
                    **Errors found:**
                    • **"don't knows"** — double negation error
                      — Use "doesn't" (not "don't") for he/she/it
                      — After "doesn't", use base form: "know" (not "knows")
                    
                    **Grammar rule:**
                    Negative present simple:
                    • I/you/we/they → **don't** + base verb
                    • he/she/it → **doesn't** + base verb
                    
                    **IELTS tip:** Avoid double negatives in academic writing — they reduce your grammatical accuracy score.""";

                            return """
                    **How to fix sentences for IELTS:**
                    
                    Common errors to check:
                    • **Subject-verb agreement** — "he goes" not "he go"
                    • **Tense consistency** — don't mix past and present
                    • **Articles** — "a/an" for first mention, "the" for known items
                    • **Uncountable nouns** — information, advice, equipment (no -s)
                    • **Word order** — adjective before noun in English
                    
                    **Example correction:**
                    ❌ "The informations are very helpfuls"
                    ✅ "The **information** is very **helpful**"
                    
                    Paste your specific sentence and I'll correct it with a full explanation!""";
    }

    private String buildIdeasReply(String msg) {
        String lower = msg.toLowerCase();
        String topic = lower.contains("climate")    ? "climate change"
                : lower.contains("tech")       ? "technology and society"
                : lower.contains("health")     ? "health and medicine"
                : lower.contains("education")  ? "education"
                : lower.contains("environment")? "environmental problems"
                : lower.contains("urban")      ? "urban development"
                : "this IELTS topic";
        return String.format("""
                    **Ideas and vocabulary for: %s**
                    
                    **Arguments (problem/agree side)**
                    • Long-term consequences outweigh short-term economic gains
                    • Government intervention is essential for systemic change
                    • Vulnerable populations are disproportionately affected
                    
                    **Arguments (solution/disagree side)**
                    • Individual behaviour change drives broader social shifts
                    • Technological innovation offers sustainable alternatives
                    • Market forces can self-regulate with proper incentives
                    
                    **Key vocabulary**
                    • *exacerbate, mitigate, alleviate, tackle, address*
                    • *sustainable, detrimental, beneficial, unprecedented*
                    • *raise awareness, implement policies, long-term implications*
                    
                    **Suggested structure (Task 2)**
                    • **Introduction:** Hook + background + thesis statement
                    • **Body 1:** First argument + example + explanation
                    • **Body 2:** Counter-argument OR second supporting point
                    • **Conclusion:** Restate + broader implication (no new ideas)
                    
                    **Example thesis:**
                    "Although [topic] presents significant challenges, targeted government policies combined with individual responsibility can effectively address the core issues."
                    """, topic);
    }

    private String buildAnswerReply(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("band") && lower.contains("differ"))
            return """
                **Band score differences in IELTS Writing:**
                
                **Band 5** — Partially addresses the task; limited vocabulary; frequent errors that impede meaning
                **Band 6** — Adequately addresses all parts; adequate range; errors present but meaning is clear
                **Band 7** — Covers all parts well; good range of vocabulary and structures; generally error-free
                **Band 8** — Handles task fully and flexibly; wide range; rare errors only; sophisticated structures
                **Band 9** — Expert user; fully operationalises all criteria; completely natural
                
                **Key difference Band 6 → 7:**
                • Uses less common vocabulary with awareness of style
                • Produces a mix of simple and complex structures
                • Presents a clear, developed position throughout
                
                **Tip:** Most candidates plateau at Band 6 due to limited vocabulary range and over-reliance on simple sentence structures.""";

                        if (lower.contains("task response"))
                            return """
                **Task Response (25% of writing score)**
                
                Task Response measures whether you:
                • Answer **all parts** of the question
                • Present a **clear position** throughout
                • Develop ideas with **relevant support**
                • Stay **on topic** without going off tangent
                
                **Common mistakes:**
                • Only answering one side of a two-part question
                • Giving a vague opinion that shifts between paragraphs
                • Using personal anecdotes instead of reasoned arguments
                
                **Band 7 requirement:** "Addresses all parts of the task... presents a clear position throughout the response"
                
                **Quick tip:** Before writing, underline every instruction in the question. Your essay must address each one explicitly.""";

                        return """
                **IELTS Quick Reference**
                
                **Exam format:**
                • Academic / General Training (Writing differs)
                • 4 sections: Listening, Reading, Writing, Speaking
                • Scored 1–9 in 0.5 increments
                
                **Writing bands:**
                • 4 criteria × 25% each
                • Task Response, Coherence & Cohesion, Lexical Resource, Grammatical Range
                
                **Common tips:**
                • Always plan for 5 minutes before writing
                • Task 1 minimum 150 words, Task 2 minimum 250 words
                • Task 2 is worth twice as much as Task 1
                • Never use informal language (gonna, wanna, etc.)
                
Ask me something more specific and I'll give you a detailed answer!""";
    }

    @Data
    static class MessageRequest {
        public String message;
        public List<Map<String, String>> history;
    }
}