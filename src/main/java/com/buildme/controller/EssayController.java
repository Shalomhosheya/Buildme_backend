package com.buildme.controller;

import com.buildme.dto.EvaluateRequest;
import com.buildme.dto.RewriteRequest;
import com.buildme.model.User;
import com.buildme.repository.EssayEvalRepo;
import com.buildme.repository.UserRepository;
import com.buildme.service.BadgeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/essay")
@RequiredArgsConstructor
public class EssayController {

    private final UserRepository    userRepository;
    private final BadgeService      badgeService;
    private final EssayEvalRepo evalRepo;
    private final RestTemplate      restTemplate;

    @Value("https://6d49-34-21-151-187.ngrok-free.app/api/predict")
    private String aiEndpoint;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ── POST /evaluate ─────────────────────────────────────────────────────
    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestBody EvaluateRequest req, Authentication auth) {
        String userId   = auth.getName();
        int    words    = req.essay.trim().split("\\s+").length;

        // Try AI model first, fall back to rule-based
        EvalResult eval = tryAiEval(req.essay, req.question, req.taskType);
        if (eval == null) eval = ruleBasedEval(req.essay, req.taskType, words);

        int pts = (int) Math.round(eval.overall * 10);

        // Persist
        EssayEval record = new EssayEval();
        record.setUserId(userId);
        record.setEssay(req.essay);
        record.setQuestion(req.question);
        record.setTaskType(req.taskType);
        record.setOverallBand(eval.overall);
        record.setWordCount(words);
        record.setPointsEarned(pts);
        evalRepo.save(record);

        // Update user points + writing skill
        EvalResult finalEval = eval;
        userRepository.findById(userId).ifPresent(user -> {
            user.setTotalPoints(user.getTotalPoints() + pts);
            updateBandScore(user, finalEval.overall);
            updateWritingProgress(user, pts);
            badgeService.checkAndAwardBadges(user);
            userRepository.save(user);
        });

        Map<String, Object> resp = buildResponse(record.getId(), eval, words, pts);
        return ResponseEntity.ok(resp);
    }

    // ── POST /rewrite ──────────────────────────────────────────────────────
    @PostMapping("/rewrite")
    public ResponseEntity<?> rewrite(@RequestBody RewriteRequest req, Authentication auth) {
        String improved = applyBandImprovements(req.essay, req.targetBand);
        List<String> changes = generateChangesList(req.essay, improved, req.targetBand);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rewrittenEssay", improved);
        resp.put("changesExplained", changes);
        return ResponseEntity.ok(resp);
    }

    // ── GET /history ───────────────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<?> history(Authentication auth) {
        return ResponseEntity.ok(evalRepo.findByUserIdOrderByEvaluatedAtDesc(auth.getName())
                .stream().limit(20).toList());
    }

    // ── AI model call ──────────────────────────────────────────────────────
    private EvalResult tryAiEval(String essay, String question, String taskType) {
        try {
            String prompt = String.format(
                    "<|system|>\n" +
                            "You are an IELTS examiner. Evaluate the essay and respond ONLY with JSON:\n" +
                            "{\"overall\":6.5,\"taskResponse\":6.5,\"coherenceCohesion\":6.0,\"lexicalResource\":6.5,\"grammaticalRange\":7.0," +
                            "\"trStrengths\":[\"...\"],\"trWeaknesses\":[\"...\"],\"trTips\":[\"...\"]," +
                            "\"ccStrengths\":[\"...\"],\"ccWeaknesses\":[\"...\"],\"ccTips\":[\"...\"]," +
                            "\"lrStrengths\":[\"...\"],\"lrWeaknesses\":[\"...\"],\"lrTips\":[\"...\"]," +
                            "\"grStrengths\":[\"...\"],\"grWeaknesses\":[\"...\"],\"grTips\":[\"...\"]}\n" +
                            "<|end|>\n" +
                            "<|user|>Task: %s\nQuestion: %s\nEssay: %s<|end|>\n" +
                            "<|assistant|>",
                    taskType, question, essay
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("ngrok-skip-browser-warning", "true");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("data", List.of(prompt));

            ResponseEntity<Map> res = restTemplate.postForEntity(
                    aiEndpoint,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                return parseAiResponse(res.getBody());
            }
        } catch (Exception e) {
            System.err.println("AI eval failed: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private EvalResult parseAiResponse(Map<String, Object> body) {
        try {
            List<?> data = (List<?>) body.get("data");
            if (data == null || data.isEmpty()) return null;

            String text = data.get(0).toString();

            // Fix smart quotes
            text = text.replace("\u201c", "\"").replace("\u201d", "\"");
            text = text.replace("\u2018", "\"").replace("\u2019", "\"");

            // Extract scores directly with regex — tolerant of truncation
            EvalResult r = new EvalResult();
            r.overall           = extractDouble(text, "overall",           6.0);
            r.taskResponse      = extractDouble(text, "taskResponse",      6.0);
            r.coherenceCohesion = extractDouble(text, "coherenceCohesion", 6.0);
            r.lexicalResource   = extractDouble(text, "lexicalResource",   6.0);
            r.grammaticalRange  = extractDouble(text, "grammaticalRange",  6.0);

            // Validate scores are in IELTS range
            if (r.overall < 1.0 || r.overall > 9.0) return null;

            r.trStrengths  = extractArray(text, "trStrengths");
            r.trWeaknesses = extractArray(text, "trWeaknesses");
            r.trTips       = extractArray(text, "trTips");
            r.ccStrengths  = extractArray(text, "ccStrengths");
            r.ccWeaknesses = extractArray(text, "ccWeaknesses");
            r.ccTips       = extractArray(text, "ccTips");
            r.lrStrengths  = extractArray(text, "lrStrengths");
            r.lrWeaknesses = extractArray(text, "lrWeaknesses");
            r.lrTips       = extractArray(text, "lrTips");
            r.grStrengths  = extractArray(text, "grStrengths");
            r.grWeaknesses = extractArray(text, "grWeaknesses");
            r.grTips       = extractArray(text, "grTips");

            System.out.println("✅ AI eval success — overall: " + r.overall);
            return r;

        } catch (Exception e) {
            System.err.println("Parse failed: " + e.getMessage());
            return null;
        }
    }

    // Extract a double value by key name using regex
    private double extractDouble(String json, String key, double fallback) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"" + key + "\"\\s*:\\s*([0-9]+\\.?[0-9]*)"
            );
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) return Double.parseDouble(m.group(1));
        } catch (Exception ignored) {}
        return fallback;
    }

    // Extract array items by key name — handles truncation gracefully
    private List<String> extractArray(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            // Find the array start
            int keyIdx = json.indexOf("\"" + key + "\"");
            if (keyIdx < 0) return result;

            int arrStart = json.indexOf('[', keyIdx);
            if (arrStart < 0) return result;

            int arrEnd = json.indexOf(']', arrStart);
            // If no closing bracket (truncated), use what we have
            String arrContent = arrEnd > 0
                    ? json.substring(arrStart + 1, arrEnd)
                    : json.substring(arrStart + 1);

            // Extract each quoted string item
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"((?:[^\"\\\\]|\\\\.)*)\""
            );
            java.util.regex.Matcher m = p.matcher(arrContent);
            while (m.find()) {
                String val = m.group(1).trim();
                if (!val.isEmpty() && !val.equals("...")) {
                    result.add(val);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    // ── Jackson helpers ────────────────────────────────────────────────────────
    private double getDouble(com.fasterxml.jackson.databind.JsonNode node,
                             String field, double fallback) {
        try {
            return node.has(field) ? node.get(field).asDouble() : fallback;
        } catch (Exception e) { return fallback; }
    }

    private List<String> getArray(com.fasterxml.jackson.databind.JsonNode node,
                                  String field) {
        List<String> result = new ArrayList<>();
        try {
            if (node.has(field) && node.get(field).isArray()) {
                node.get(field).forEach(item -> {
                    String val = item.asText().trim();
                    if (!val.isEmpty() && !val.equals("...")) result.add(val);
                });
            }
        } catch (Exception ignored) {}
        return result;
    }

    // ── Rule-based fallback ────────────────────────────────────────────────
    private EvalResult ruleBasedEval(String essay, String taskType, int words) {
        Random rng = new Random(essay.hashCode());
        double base = estimateBase(essay, taskType, words);
        EvalResult r = new EvalResult();
        r.overall           = round(base);
        r.taskResponse      = round(base + (rng.nextDouble() - 0.4) * 0.5);
        r.coherenceCohesion = round(base + (rng.nextDouble() - 0.5) * 0.5);
        r.lexicalResource   = round(base + (rng.nextDouble() - 0.4) * 0.5);
        r.grammaticalRange  = round(base + (rng.nextDouble() - 0.3) * 0.5);

        r.trStrengths  = List.of("Addresses the question with relevant ideas");
        r.trWeaknesses = List.of("Some ideas could be developed with more specific examples");
        r.trTips       = List.of("Use the PEEL structure: Point, Evidence, Explanation, Link");

        r.ccStrengths  = List.of("Ideas are generally logically ordered");
        r.ccWeaknesses = List.of("Over-reliance on basic connectors such as 'firstly' and 'however'");
        r.ccTips       = List.of("Try substituting, 'In the first instance', 'Consequently', 'Notwithstanding'");

        r.lrStrengths  = List.of("Adequate vocabulary range for the topic");
        r.lrWeaknesses = List.of("Some repetition of key terms — use synonyms");
        r.lrTips       = List.of("Learn collocations for common IELTS topics (environment, technology, health)");

        r.grStrengths  = List.of("Mix of simple and complex sentence structures");
        r.grWeaknesses = List.of("Some errors with article use and subject-verb agreement");
        r.grTips       = List.of("Review uncountable nouns: information, advice, equipment (no plural -s)");
        return r;
    }

    private double estimateBase(String essay, String taskType, int words) {
        double score = 5.0;
        int min = taskType.equals("Task 1") ? 150 : 250;
        if (words >= min)       score += 0.5;
        if (words >= min + 50)  score += 0.3;
        if (essay.contains(",")) score += 0.1;
        if (essay.matches("(?i).*\\b(furthermore|however|therefore|consequently|nevertheless)\\b.*")) score += 0.3;
        if (essay.matches("(?i).*\\b(firstly|secondly|finally|in conclusion)\\b.*")) score += 0.2;
        if (!essay.matches("(?i).*(gonna|wanna|stuff|things).*")) score += 0.2;
        return Math.min(8.0, Math.max(4.0, score));
    }

    // ── Rewrite helpers ────────────────────────────────────────────────────
    private String applyBandImprovements(String essay, int targetBand) {
        String out = essay;
        if (targetBand >= 7) {
            out = out.replaceAll("(?i)\\bgood\\b", "beneficial");
            out = out.replaceAll("(?i)\\bbad\\b", "detrimental");
            out = out.replaceAll("(?i)\\bbig\\b", "substantial");
            out = out.replaceAll("(?i)\\bsmall\\b", "minimal");
            out = out.replaceAll("(?i)\\bthings\\b", "aspects");
            out = out.replaceAll("(?i)\\ba lot of\\b", "a considerable number of");
            out = out.replaceAll("(?i)\\bfirstly,", "In the first instance,");
            out = out.replaceAll("(?i)\\bsecondly,", "Furthermore,");
            out = out.replaceAll("(?i)\\bin conclusion, i think", "In conclusion, it is evident that");
            out = out.replaceAll("(?i)\\bin conclusion, i believe", "In conclusion, it can be argued that");
        }
        if (targetBand >= 8) {
            out = out.replaceAll("(?i)\\bimportant\\b", "of paramount importance");
            out = out.replaceAll("(?i)\\bshow\\b", "demonstrate");
            out = out.replaceAll("(?i)\\buse\\b", "utilise");
            out = out.replaceAll("(?i)\\bhelp\\b", "facilitate");
            out = out.replaceAll("(?i)\\bget\\b", "obtain");
            out = out.replaceAll("(?i)\\ba considerable number of\\b", "an overwhelming majority of");
        }
        return out;
    }

    private List<String> generateChangesList(String original, String improved, int band) {
        List<String> changes = new ArrayList<>();
        if (!original.equals(improved)) {
            changes.add("Replaced basic adjectives with precise academic alternatives");
            changes.add("Upgraded connectors to a more varied range of cohesive devices");
            if (band >= 7) changes.add("Removed first-person from conclusions for formal academic register");
            if (band >= 8) changes.add("Substituted common verbs with higher-register academic equivalents");
        }
        if (changes.isEmpty()) changes.add("No changes needed — essay is already at this level");
        return changes;
    }

    // ── Response builder ───────────────────────────────────────────────────
    private Map<String, Object> buildResponse(String id, EvalResult e, int words, int pts) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("evaluationId", id);
        resp.put("overallBand",  e.overall);
        resp.put("wordCount",    words);
        resp.put("pointsEarned", pts);
        resp.put("taskResponse",     criterion(e.taskResponse,      e.trStrengths, e.trWeaknesses, e.trTips));
        resp.put("coherenceCohesion",criterion(e.coherenceCohesion, e.ccStrengths, e.ccWeaknesses, e.ccTips));
        resp.put("lexicalResource",  criterion(e.lexicalResource,   e.lrStrengths, e.lrWeaknesses, e.lrTips));
        resp.put("grammaticalRange", criterion(e.grammaticalRange,  e.grStrengths, e.grWeaknesses, e.grTips));
        resp.put("inlineIssues",     List.of());
        resp.put("evaluatedAt",      LocalDateTime.now().format(FMT));
        return resp;
    }

    private Map<String, Object> criterion(double score, List<String> strengths,
                                          List<String> weaknesses, List<String> tips) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score",     score);
        m.put("band",      "Band " + score);
        m.put("strengths", strengths);
        m.put("weaknesses",weaknesses);
        m.put("tips",      tips);
        return m;
    }

    // ── User update helpers ────────────────────────────────────────────────
    private void updateBandScore(User user, double band) {
        double current = user.getEstimatedBandScore();
        user.setEstimatedBandScore(current == 0 ? band
                : Math.round((current * 0.7 + band * 0.3) * 10.0) / 10.0);
    }

    private void updateWritingProgress(User user, int pts) {
        User.SkillProgress sp = user.getWriting();
        sp.setPoints(sp.getPoints() + pts);
        int total = sp.getPoints();
        if (total >= 700)      { sp.setLevel(3); sp.setLevelName("Multistructural"); sp.setMaxPoints(1200); }
        else if (total >= 300) { sp.setLevel(2); sp.setLevelName("Unistructural");   sp.setMaxPoints(700);  }
    }

    // ── JSON utils ─────────────────────────────────────────────────────────
    private double parseDouble(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\":");
            if (idx < 0) return 5.5;
            String sub = json.substring(idx + key.length() + 3).trim();
            return Double.parseDouble(sub.split("[,}]")[0].trim());
        } catch (Exception e) { return 5.5; }
    }

    private List<String> parseArray(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\":[");
            if (idx < 0) return List.of();
            int start = json.indexOf('[', idx) + 1;
            int end   = json.indexOf(']', start);
            String arr = json.substring(start, end);
            List<String> result = new ArrayList<>();
            for (String item : arr.split(",")) {
                String clean = item.trim().replaceAll("^\"|\"$", "");
                if (!clean.isEmpty()) result.add(clean);
            }
            return result;
        } catch (Exception e) { return List.of(); }
    }

    private double round(double v) {
        return Math.round(Math.max(3.0, Math.min(9.0, v)) * 2.0) / 2.0;
    }

    // ── Inner classes ──────────────────────────────────────────────────────

    static class EvalResult {
        double overall, taskResponse, coherenceCohesion, lexicalResource, grammaticalRange;
        List<String> trStrengths, trWeaknesses, trTips;
        List<String> ccStrengths, ccWeaknesses, ccTips;
        List<String> lrStrengths, lrWeaknesses, lrTips;
        List<String> grStrengths, grWeaknesses, grTips;
    }


    @Document(collection = "essay_evaluations_v2") @Data
    public static class EssayEval {
        @Id private String id;
        private String userId, essay, question, taskType;
        private double overallBand;
        private int wordCount, pointsEarned;
        @CreatedDate private LocalDateTime evaluatedAt;
    }


}