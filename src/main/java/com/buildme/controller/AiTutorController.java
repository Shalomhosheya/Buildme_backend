//package com.buildme.controller;
//
//import com.buildme.model.EssayEvaluation;
//import com.buildme.model.User;
//import com.buildme.repository.EssayEvaluationRepository;
//import com.buildme.repository.UserRepository;
//import com.buildme.service.BadgeService;
//import jakarta.validation.Valid;
//import jakarta.validation.constraints.NotBlank;
//import lombok.Data;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//
//@RestController
//@RequestMapping("/api/ai")
//@RequiredArgsConstructor
//public class AiTutorController {
//
//    private final EssayEvaluationRepository evaluationRepository;
//    private final UserRepository userRepository;
//    private final BadgeService badgeService;
//    private final RestTemplate restTemplate;
//
//    @Value("${app.ai.endpoint}")
//    private String aiEndpoint;
//
//    @PostMapping("/evaluate")
//    public ResponseEntity<?> evaluateEssay(@Valid @RequestBody EssayRequest req,
//                                           Authentication auth) {
//        String userId = auth.getName();
//
//        EvaluationResult result = callPhiModel(req.question, req.essay, req.taskType);
//
//        int pointsEarned = (int) Math.round(result.overallBand * 10);
//
//        EssayEvaluation evaluation = EssayEvaluation.builder()
//                .userId(userId)
//                .question(req.question != null ? req.question : "")
//                .essay(req.essay)
//                .taskType(req.taskType != null ? req.taskType : "Task 2")
//                .overallBand(result.overallBand)
//                .taskResponse(result.taskResponse)
//                .coherenceCohesion(result.coherenceCohesion)
//                .lexicalResource(result.lexicalResource)
//                .grammaticalRange(result.grammaticalRange)
//                .feedback(result.feedback)
//                .pointsEarned(pointsEarned)
//                .build();
//
//        evaluationRepository.save(evaluation);
//
//        // Add points to user and update estimated band score
//        userRepository.findById(userId).ifPresent(user -> {
//            user.setTotalPoints(user.getTotalPoints() + pointsEarned);
//            updateBandScore(user, result.overallBand);
//            badgeService.checkAndAwardBadges(user);
//            userRepository.save(user);
//        });
//
//        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
//                "evaluationId",      evaluation.getId(),
//                "overallBand",       result.overallBand,
//                "taskResponse",      result.taskResponse,
//                "coherenceCohesion", result.coherenceCohesion,
//                "lexicalResource",   result.lexicalResource,
//                "grammaticalRange",  result.grammaticalRange,
//                "feedback",          result.feedback,
//                "pointsEarned",      pointsEarned
//        ));
//    }
//
//    @GetMapping("/history")
//    public ResponseEntity<List<EssayEvaluation>> getHistory(Authentication auth) {
//        return ResponseEntity.ok(
//                evaluationRepository.findByUserIdOrderByEvaluatedAtDesc(auth.getName()));
//    }
//
//    @PostMapping("/chat")
//    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
//        String message = body.getOrDefault("message", "");
//        String reply = generateChatResponse(message);
//        return ResponseEntity.ok(Map.of("reply", reply));
//    }
//
//    // ── Private helpers ────────────────────────────────────────────────────
//
//    private EvaluationResult callPhiModel(String question, String essay, String taskType) {
//        try {
//            String prompt = buildPrompt(question, essay, taskType);
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            Map<String, Object> body = Map.of("data", List.of(prompt));
//            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
//
//            ResponseEntity<Map> response = restTemplate.postForEntity(aiEndpoint, entity, Map.class);
//
//            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                return parseModelResponse(response.getBody());
//            }
//        } catch (Exception e) {
//            // Fall back to mock if model is unavailable
//        }
//        return mockEvaluation(essay);
//    }
//
//    private String buildPrompt(String question, String essay, String taskType) {
//        return String.format("""
//                <|system|>
//                You are an expert IELTS Writing tutor. Evaluate the essay and respond ONLY with JSON in this format:
//                {"overall": 6.5, "taskResponse": 6.5, "coherenceCohesion": 6.5, "lexicalResource": 6.0, "grammaticalRange": 7.0, "feedback": "detailed feedback here"}
//                <|end|>
//                <|user|>
//                Task type: %s
//                Question: %s
//                Essay: %s
//                <|end|>
//                <|assistant|>""",
//                taskType != null ? taskType : "Task 2",
//                question != null ? question : "No question provided",
//                essay);
//    }
//
//    @SuppressWarnings("unchecked")
//    private EvaluationResult parseModelResponse(Map<String, Object> response) {
//        try {
//            List<?> data = (List<?>) response.get("data");
//            if (data != null && !data.isEmpty()) {
//                String text = data.get(0).toString();
//                // Extract JSON from response
//                int start = text.indexOf('{');
//                int end   = text.lastIndexOf('}') + 1;
//                if (start >= 0 && end > start) {
//                    String json = text.substring(start, end);
//                    // Simple field extraction (avoids extra dependency)
//                    return new EvaluationResult(
//                            extractDouble(json, "overall"),
//                            extractDouble(json, "taskResponse"),
//                            extractDouble(json, "coherenceCohesion"),
//                            extractDouble(json, "lexicalResource"),
//                            extractDouble(json, "grammaticalRange"),
//                            extractString(json, "feedback")
//                    );
//                }
//            }
//        } catch (Exception ignored) {}
//        return mockEvaluation("");
//    }
//
//    private double extractDouble(String json, String key) {
//        try {
//            int idx = json.indexOf("\"" + key + "\":");
//            if (idx < 0) return 5.5;
//            String sub = json.substring(idx + key.length() + 3).trim();
//            return Double.parseDouble(sub.split("[,}]")[0].trim());
//        } catch (Exception e) { return 5.5; }
//    }
//
//    private String extractString(String json, String key) {
//        try {
//            int idx = json.indexOf("\"" + key + "\":\"");
//            if (idx < 0) return "No feedback available.";
//            int start = idx + key.length() + 4;
//            int end = json.indexOf("\"", start);
//            return json.substring(start, end);
//        } catch (Exception e) { return "No feedback available."; }
//    }
//
//    private EvaluationResult mockEvaluation(String essay) {
//        Random rng = new Random();
//        int words = essay.trim().isEmpty() ? 0 : essay.trim().split("\\s+").length;
//        double base = words > 200 ? 5.5 + rng.nextDouble() * 1.5 : 4.0 + rng.nextDouble();
//        return new EvaluationResult(
//                round(base),
//                round(base + (rng.nextDouble() - 0.5)),
//                round(base + (rng.nextDouble() - 0.5)),
//                round(base + (rng.nextDouble() - 0.5)),
//                round(base + rng.nextDouble() * 0.5),
//                "Your essay demonstrates " + (base >= 6 ? "good" : "developing") +
//                " command of English. Focus on task response and coherence to improve your score."
//        );
//    }
//
//    private double round(double v) {
//        return Math.round(Math.max(3.0, Math.min(9.0, v)) * 2.0) / 2.0;
//    }
//
//    private void updateBandScore(User user, double newBand) {
//        double current = user.getEstimatedBandScore();
//        // Running average weighted toward recent
//        user.setEstimatedBandScore(current == 0 ? newBand : Math.round((current * 0.7 + newBand * 0.3) * 10.0) / 10.0);
//    }
//
//    private String generateChatResponse(String message) {
//        String m = message.toLowerCase();
//        if (m.contains("task response")) return "To improve Task Response: fully address all parts of the question, maintain a clear position, and support every argument with specific examples.";
//        if (m.contains("linking") || m.contains("cohesion")) return "Key linking phrases — Adding: Furthermore, Moreover. Contrasting: However, Nevertheless. Cause/effect: Therefore, Consequently. Examples: For instance, To illustrate.";
//        if (m.contains("band 7") || m.contains("band7")) return "Band 7 requires: clear position throughout, well-developed ideas with examples, varied vocabulary with less common items, and flexible grammar with minimal errors.";
//        if (m.contains("vocabular") || m.contains("lexical")) return "To boost Lexical Resource: use less common synonyms, avoid repetition, and show awareness of collocation (e.g., 'make a decision', not 'do a decision').";
//        return "Great question! Focus on the four IELTS band criteria: Task Response, Coherence & Cohesion, Lexical Resource, and Grammatical Range & Accuracy. Which would you like to explore further?";
//    }
//
//    record EvaluationResult(double overallBand, double taskResponse, double coherenceCohesion,
//                            double lexicalResource, double grammaticalRange, String feedback) {}
//
//    @Data static class EssayRequest {
//        public String question;
//        @NotBlank public String essay;
//        public String taskType;
//    }
//}
