package com.buildme.controller;

import com.buildme.model.Certificate;
import com.buildme.model.User;
import com.buildme.repository.CertificateRepository;
import com.buildme.repository.QuizAttemptRepository;
import com.buildme.repository.UserRepository;
import com.buildme.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateRepository certificateRepository;
    private final UserRepository        userRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final BadgeService          badgeService;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        String userId = auth.getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean eligible = isEligible(user);
        boolean issued   = certificateRepository.existsByUserId(userId);

        return ResponseEntity.ok(Map.of(
                "eligible",        eligible,
                "issued",          issued,
                "requirements",    buildRequirements(user),
                "readinessPercent", calculateReadiness(user)
        ));
    }

    @PostMapping("/issue")
    public ResponseEntity<?> issueCertificate(Authentication auth) {
        String userId = auth.getName();

        if (certificateRepository.existsByUserId(userId)) {
            return certificateRepository.findByUserId(userId)
                    .map(cert -> ResponseEntity.ok((Object) buildCertResponse(cert)))
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!isEligible(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "Not yet eligible — complete all 4 skills to Level 3 first",
                    "requirements", buildRequirements(user)
            ));
        }

        long quizCount = quizAttemptRepository.countByUserId(userId);

        String certId = "BM-" + java.time.Year.now().getValue()
                + "-" + user.getName().substring(0, Math.min(2, user.getName().length())).toUpperCase()
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        Certificate cert = Certificate.builder()
                .certId(certId)
                .userId(userId)
                .userName(user.getName())
                .estimatedBand(user.getEstimatedBandScore())
                .totalPoints(user.getTotalPoints())
                .quizzesCompleted((int) quizCount)
                .verified(true)
                .build();

        certificateRepository.save(cert);

        // Award certified badge
        if (!user.getEarnedBadges().contains("Certified")) {
            user.getEarnedBadges().add("Certified");
            userRepository.save(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(buildCertResponse(cert));
    }

    @GetMapping("/verify/{certId}")
    public ResponseEntity<?> verify(@PathVariable String certId) {
        return certificateRepository.findByCertId(certId)
                .map(cert -> ResponseEntity.ok(Map.of(
                        "valid",           cert.isVerified(),
                        "certId",          cert.getCertId(),
                        "userName",        cert.getUserName(),
                        "estimatedBand",   cert.getEstimatedBand(),
                        "totalPoints",     cert.getTotalPoints(),
                        "quizzesCompleted",cert.getQuizzesCompleted(),
                        "issuedAt",        cert.getIssuedAt()
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("valid", false, "message", "Certificate not found")));
    }

    private boolean isEligible(User user) {
        return user.getWriting().getLevel()   >= 3
            && user.getReading().getLevel()   >= 3
            && user.getListening().getLevel() >= 3
            && user.getSpeaking().getLevel()  >= 3
            && user.getEstimatedBandScore()   >= 6.0;
    }

    private Map<String, Object> buildRequirements(User user) {
        return Map.of(
                "writingLevel3",   user.getWriting().getLevel()   >= 3,
                "readingLevel3",   user.getReading().getLevel()   >= 3,
                "listeningLevel3", user.getListening().getLevel() >= 3,
                "speakingLevel3",  user.getSpeaking().getLevel()  >= 3,
                "bandScore6",      user.getEstimatedBandScore()   >= 6.0
        );
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

    private Map<String, Object> buildCertResponse(Certificate cert) {
        return Map.of(
                "certId",           cert.getCertId(),
                "userName",         cert.getUserName(),
                "estimatedBand",    cert.getEstimatedBand(),
                "totalPoints",      cert.getTotalPoints(),
                "quizzesCompleted", cert.getQuizzesCompleted(),
                "verifyUrl",        "https://buildme.app/verify/" + cert.getCertId(),
                "issuedAt",         cert.getIssuedAt()
        );
    }
}
