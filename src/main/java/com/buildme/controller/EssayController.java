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

    @Value("${app.ai.endpoint:http://localhost:7860/api/predict}")
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
            String prompt = String.format("""
                    <|system|>
                    You are an IELTS examiner. Evaluate the essay and respond ONLY with JSON:
                    {"overall":6.5,"taskResponse":6.5,"coherenceCohesion":6.0,"lexicalResource":6.5,"grammaticalRange":7.0,
                     "trStrengths":["..."],"trWeaknesses":["..."],"trTips":["..."],
                     "ccStrengths":["..."],"ccWeaknesses":["..."],"ccTips":["..."],
                     "lrStrengths":["..."],"lrWeaknesses":["..."],"lrTips":["..."],
                     "grStrengths":["..."],"grWeaknesses":["..."],"grTips":["..."]}
                    <|end|>
                    <|user|>Task: %s\nQuestion: %s\nEssay: %s<|end|>
                    <|assistant|>""", taskType, question, essay);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    aiEndpoint,
                    new HttpEntity<>(Map.of("data", List.of(prompt)), headers),
                    Map.class);

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                return parseAiResponse(res.getBody());
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private EvalResult parseAiResponse(Map<String, Object> body) {
        try {
            List<?> data = (List<?>) body.get("data");
            if (data == null || data.isEmpty()) return null;
            String text  = data.get(0).toString();
            int start    = text.indexOf('{');
            int end      = text.lastIndexOf('}') + 1;
            if (start < 0 || end <= start) return null;
            String json  = text.substring(start, end);
            EvalResult r = new EvalResult();
            r.overall             = parseDouble(json, "overall");
            r.taskResponse        = parseDouble(json, "taskResponse");
            r.coherenceCohesion   = parseDouble(json, "coherenceCohesion");
            r.lexicalResource     = parseDouble(json, "lexicalResource");
            r.grammaticalRange    = parseDouble(json, "grammaticalRange");
            r.trStrengths  = parseArray(json, "trStrengths");
            r.trWeaknesses = parseArray(json, "trWeaknesses");
            r.trTips       = parseArray(json, "trTips");
            r.ccStrengths  = parseArray(json, "ccStrengths");
            r.ccWeaknesses = parseArray(json, "ccWeaknesses");
            r.ccTips       = parseArray(json, "ccTips");
            r.lrStrengths  = parseArray(json, "lrStrengths");
            r.lrWeaknesses = parseArray(json, "lrWeaknesses");
            r.lrTips       = parseArray(json, "lrTips");
            r.grStrengths  = parseArray(json, "grStrengths");
            r.grWeaknesses = parseArray(json, "grWeaknesses");
            r.grTips       = parseArray(json, "grTips");
            return r;
        } catch (Exception e) { return null; }
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