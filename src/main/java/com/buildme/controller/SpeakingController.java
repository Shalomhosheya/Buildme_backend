package com.buildme.controller;

import com.buildme.model.SpeakingEval;
import com.buildme.model.User;
import com.buildme.repository.SpeakingEvalRepository;
import com.buildme.repository.UserRepository;
import com.buildme.service.BadgeService;
import com.buildme.service.GroqIeltsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/speaking")
@RequiredArgsConstructor
@Slf4j
public class SpeakingController {

    private final UserRepository userRepository;
    private final BadgeService badgeService;
    private final SpeakingEvalRepository evalRepository;
    private final GroqIeltsService geminiService;

    @GetMapping("/questions")
    public ResponseEntity<List<String>> getQuestions() {
        return ResponseEntity.ok(List.of(
                "Describe a place you enjoy visiting. What makes it special to you?",
                "Talk about a skill you would like to learn. Why is it important to you?"
        ));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("question") String question,
            Authentication auth) {

        String userId = auth.getName();

        try {
            // ✅ Single Gemini call — transcription + evaluation together
            Map<String, Object> aiResult = geminiService.transcribeAndEvaluate(audio, question);

            String transcript    = (String) aiResult.get("transcript");
            double fluency       = (double) aiResult.get("fluency");
            double pronunciation = (double) aiResult.get("pronunciation");
            double vocabulary    = (double) aiResult.get("vocabulary");
            double grammar       = (double) aiResult.get("grammar");
            double overall       = (double) aiResult.get("overallBand");
            String feedback      = (String) aiResult.get("feedback");
            List<String> improvements = (List<String>) aiResult.get("improvements");
            int pointsEarned     = (int) Math.round(overall * 10);

            // ✅ Save to DB
            SpeakingEval eval = new SpeakingEval();
            eval.setUserId(userId);
            eval.setQuestion(question);
            eval.setTranscript(transcript);
            eval.setFluency(fluency);
            eval.setPronunciation(pronunciation);
            eval.setVocabulary(vocabulary);
            eval.setGrammar(grammar);
            eval.setOverallBand(overall);
            eval.setFeedback(feedback);
            eval.setImprovements(improvements);
            eval.setPointsEarned(pointsEarned);
            evalRepository.save(eval);

            // ✅ Update user points + badges
            userRepository.findById(userId).ifPresent(user -> {
                user.setTotalPoints(user.getTotalPoints() + pointsEarned);
                updateSpeakingProgress(user, pointsEarned);
                badgeService.checkAndAwardBadges(user);
                userRepository.save(user);
            });

            // ✅ Build response
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("evaluationId",  eval.getId());
            resp.put("transcript",    transcript);
            resp.put("fluency",       fluency);
            resp.put("pronunciation", pronunciation);
            resp.put("vocabulary",    vocabulary);
            resp.put("grammar",       grammar);
            resp.put("overallBand",   overall);
            resp.put("feedback",      feedback);
            resp.put("improvements",  improvements);
            resp.put("pointsEarned",  pointsEarned);

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Speaking evaluation failed for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Evaluation failed: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<SpeakingEval>> getHistory(Authentication auth) {
        return evalRepository
                .findByUserIdOrderByEvaluatedAtDesc(auth.getName())
                .stream().limit(10)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        ResponseEntity::ok));
    }

    private void updateSpeakingProgress(User user, int points) {
        User.SkillProgress sp = user.getSpeaking();
        sp.setPoints(sp.getPoints() + points);
        int newPts = sp.getPoints();
        if (newPts >= 700) {
            sp.setLevel(3);
            sp.setLevelName("Multistructural");
            sp.setMaxPoints(1200);
        } else if (newPts >= 300) {
            sp.setLevel(2);
            sp.setLevelName("Unistructural");
            sp.setMaxPoints(700);
        }
    }
}