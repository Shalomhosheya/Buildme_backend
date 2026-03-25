package com.buildme.controller;

import com.buildme.model.User;
import com.buildme.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final UserRepository           userRepository;
    private final QuizAttemptRepository    quizAttemptRepository;
    private final EssayEvaluationRepository essayEvaluationRepository;
    private final NoteRepository           noteRepository;
    private final CertificateRepository    certificateRepository;

    @GetMapping
    public ResponseEntity<?> getPortfolio(Authentication auth) {
        String userId = auth.getName();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        long quizCount  = quizAttemptRepository.countByUserId(userId);
        long noteCount  = noteRepository.countByUserId(userId);
        long essayCount = essayEvaluationRepository.countByUserId(userId);
        boolean hasCert = certificateRepository.existsByUserId(userId);

        int readinessPercent = calculateReadiness(user);

        return ResponseEntity.ok(Map.of(
                "user", Map.of(
                        "id",                 user.getId(),
                        "name",               user.getName(),
                        "email",              user.getEmail(),
                        "totalPoints",        user.getTotalPoints(),
                        "streakDays",         user.getStreakDays(),
                        "estimatedBandScore", user.getEstimatedBandScore(),
                        "earnedBadges",       user.getEarnedBadges(),
                        "createdAt",          user.getCreatedAt()
                ),
                "skills", Map.of(
                        "writing",   user.getWriting(),
                        "reading",   user.getReading(),
                        "listening", user.getListening(),
                        "speaking",  user.getSpeaking()
                ),
                "stats", Map.of(
                        "quizzesCompleted",    quizCount,
                        "essaysEvaluated",     essayCount,
                        "notesCreated",        noteCount,
                        "certificateIssued",   hasCert,
                        "readinessPercent",    readinessPercent
                ),
                "recentActivity", Map.of(
                        "recentQuizzes", quizAttemptRepository
                                .findByUserIdOrderByCompletedAtDesc(userId)
                                .stream().limit(5).toList(),
                        "recentEssays", essayEvaluationRepository
                                .findByUserIdOrderByEvaluatedAtDesc(userId)
                                .stream().limit(3).toList()
                )
        ));
    }

    private int calculateReadiness(User user) {
        int score = 0;
        score += Math.min(user.getWriting().getLevel(),   3) * 8;
        score += Math.min(user.getReading().getLevel(),   3) * 8;
        score += Math.min(user.getListening().getLevel(), 3) * 8;
        score += Math.min(user.getSpeaking().getLevel(),  3) * 8;
        if (user.getEstimatedBandScore() >= 6.0) score += 10;
        if (user.getEstimatedBandScore() >= 7.0) score += 6;
        return Math.min(100, score);
    }
}
