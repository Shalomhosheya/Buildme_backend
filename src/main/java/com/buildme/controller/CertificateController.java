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

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        String userId = auth.getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean eligible = isEligible(user);
        boolean issued   = certificateRepository.existsByUserId(userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("eligible",         eligible);
        resp.put("issued",           issued);
        resp.put("requirements",     buildRequirements(user));
        resp.put("readinessPercent", calculateReadiness(user));
        return ResponseEntity.ok(resp);
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
            Map<String, Object> err = new HashMap<>();
            err.put("message",      "Not yet eligible — complete all 4 skills to Level 3 first");
            err.put("requirements", buildRequirements(user));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
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

        if (!user.getEarnedBadges().contains("Certified")) {
            user.getEarnedBadges().add("Certified");
            userRepository.save(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(buildCertResponse(cert));
    }

    @GetMapping("/verify/{certId}")
    public ResponseEntity<?> verify(@PathVariable String certId) {
        return certificateRepository.findByCertId(certId)
                .map(cert -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("valid",            cert.isVerified());
                    resp.put("certId",           cert.getCertId());
                    resp.put("userName",         cert.getUserName());
                    resp.put("estimatedBand",    cert.getEstimatedBand());
                    resp.put("totalPoints",      cert.getTotalPoints());
                    resp.put("quizzesCompleted", cert.getQuizzesCompleted());
                    resp.put("issuedAt",         cert.getIssuedAt() != null
                            ? cert.getIssuedAt().format(FMT) : null);
                    return ResponseEntity.ok((Object) resp);
                })
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
        Map<String, Object> m = new HashMap<>();
        m.put("writingLevel3",   user.getWriting().getLevel()   >= 3);
        m.put("readingLevel3",   user.getReading().getLevel()   >= 3);
        m.put("listeningLevel3", user.getListening().getLevel() >= 3);
        m.put("speakingLevel3",  user.getSpeaking().getLevel()  >= 3);
        m.put("bandScore6",      user.getEstimatedBandScore()   >= 6.0);
        return m;
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
        Map<String, Object> m = new HashMap<>();
        m.put("certId",           cert.getCertId());
        m.put("userName",         cert.getUserName());
        m.put("estimatedBand",    cert.getEstimatedBand());
        m.put("totalPoints",      cert.getTotalPoints());
        m.put("quizzesCompleted", cert.getQuizzesCompleted());
        m.put("verifyUrl",        "https://buildme.app/verify/" + cert.getCertId());
        m.put("issuedAt",         cert.getIssuedAt() != null
                ? cert.getIssuedAt().format(FMT) : null);
        return m;
    }
}