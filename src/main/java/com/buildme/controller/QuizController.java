package com.buildme.controller;

import com.buildme.model.QuizAttempt;
import com.buildme.model.User;
import com.buildme.repository.QuizAttemptRepository;
import com.buildme.repository.UserRepository;
import com.buildme.service.BadgeService;
import com.buildme.service.SkillProgressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;
    private final BadgeService badgeService;
    private final SkillProgressService skillProgressService;

    @GetMapping("/attempts")
    public ResponseEntity<List<QuizAttempt>> getAttempts(Authentication auth) {
        return ResponseEntity.ok(
                quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(auth.getName()));
    }

    @GetMapping("/attempts/{skill}")
    public ResponseEntity<List<QuizAttempt>> getAttemptsBySkill(
            @PathVariable String skill, Authentication auth) {
        return ResponseEntity.ok(
                quizAttemptRepository.findByUserIdAndSkill(auth.getName(), skill));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitQuiz(@Valid @RequestBody QuizSubmitRequest req,
                                        Authentication auth) {
        String userId = auth.getName();

        double pct = (double) req.score / req.totalQuestions;
        boolean passed = pct >= 0.6;
        int pointsEarned = passed ? (int) Math.round(req.score * (100.0 / req.totalQuestions)) : 0;

        QuizAttempt attempt = QuizAttempt.builder()
                .userId(userId)
                .quizId(req.quizId)
                .skill(req.skill)
                .soloLevel(req.soloLevel)
                .score(req.score)
                .totalQuestions(req.totalQuestions)
                .pointsEarned(pointsEarned)
                .passed(passed)
                .build();

        quizAttemptRepository.save(attempt);

        if (pointsEarned > 0) {
            User user = skillProgressService.addSkillPoints(userId, req.skill, pointsEarned);
            badgeService.checkAndAwardBadges(user);
            userRepository.save(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "attemptId", attempt.getId(),
                "passed", passed,
                "pointsEarned", pointsEarned,
                "percentage", Math.round(pct * 100)
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(Authentication auth) {
        String userId = auth.getName();
        long total = quizAttemptRepository.countByUserId(userId);
        List<QuizAttempt> attempts = quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(userId);
        long passed = attempts.stream().filter(QuizAttempt::isPassed).count();

        return ResponseEntity.ok(Map.of(
                "totalAttempts", total,
                "passedAttempts", passed,
                "recentAttempts", attempts.stream().limit(5).toList()
        ));
    }

    @Data static class QuizSubmitRequest {
        @NotBlank public String quizId;
        @NotBlank public String skill;
        public int soloLevel;
        public int score;
        public int totalQuestions;
    }
}
