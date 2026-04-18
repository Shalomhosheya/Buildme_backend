package com.buildme.controller;

import com.buildme.dto.SubmitRequest;
import com.buildme.model.ListeningAttempt;
import com.buildme.model.User;
import com.buildme.repository.ListeningAttemptRepository;
import com.buildme.repository.UserRepository;
import com.buildme.service.BadgeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/listening")
@RequiredArgsConstructor
public class ListeningController {

    private final UserRepository           userRepository;
    private final BadgeService             badgeService;
    private final ListeningAttemptRepository attemptRepository;

    // ── GET /tracks ────────────────────────────────────────────────────────
    @GetMapping("/tracks")
    public ResponseEntity<List<Map<String, Object>>> getTracks(
            @RequestParam(required = false) String accent) {

        // In production, load from DB. For now return structured data.
        List<Map<String, Object>> all = buildTracks();
        if (accent != null && !accent.isBlank()) {
            all = all.stream()
                    .filter(t -> accent.equals(t.get("accent")))
                    .toList();
        }
        return ResponseEntity.ok(all);
    }

    // ── POST /submit ───────────────────────────────────────────────────────
    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody SubmitRequest req, Authentication auth) {
        String userId = auth.getName();

        double pct         = (double) req.score / req.total;
        boolean passed     = pct >= 0.6;
        int pointsEarned   = passed ? (int) Math.round(req.score * (100.0 / req.total)) : 0;

        ListeningAttempt attempt = new ListeningAttempt();
        attempt.setUserId(userId);
        attempt.setTrackId(req.trackId);
        attempt.setAccent(req.accent);
        attempt.setScore(req.score);
        attempt.setTotal(req.total);
        attempt.setPointsEarned(pointsEarned);
        attempt.setPassed(passed);
        attemptRepository.save(attempt);

        if (pointsEarned > 0) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setTotalPoints(user.getTotalPoints() + pointsEarned);
                updateListeningProgress(user, pointsEarned);
                badgeService.checkAndAwardBadges(user);
                userRepository.save(user);
            });
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("attemptId",    attempt.getId());
        resp.put("score",        req.score);
        resp.put("total",        req.total);
        resp.put("percentage",   Math.round(pct * 100));
        resp.put("pointsEarned", pointsEarned);
        resp.put("passed",       passed);
        return ResponseEntity.ok(resp);
    }

    // ── GET /history ───────────────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<List<ListeningAttempt>> getHistory(Authentication auth) {
        return ResponseEntity.ok(
                attemptRepository.findByUserIdOrderByCompletedAtDesc(auth.getName())
                        .stream().limit(20).toList()
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void updateListeningProgress(User user, int pts) {
        User.SkillProgress sp = user.getListening();
        sp.setPoints(sp.getPoints() + pts);
        int total = sp.getPoints();
        if (total >= 700)      { sp.setLevel(3); sp.setLevelName("Multistructural"); sp.setMaxPoints(1200); }
        else if (total >= 300) { sp.setLevel(2); sp.setLevelName("Unistructural");   sp.setMaxPoints(700);  }
    }

    private List<Map<String, Object>> buildTracks() {
        List<Map<String, Object>> tracks = new ArrayList<>();

        // British tracks
        tracks.add(track("br-1", "University accommodation", "british", 1, "Prestructural", "2:30", "Education", 4));
        tracks.add(track("br-2", "NHS appointment booking",  "british", 2, "Unistructural",   "3:10", "Health",    4));

        // Australian tracks
        tracks.add(track("au-1", "Tourist information centre", "australian", 1, "Prestructural", "2:45", "Tourism", 4));
        tracks.add(track("au-2", "Job interview preparation",  "australian", 2, "Unistructural",  "3:20", "Work",   4));

        // American tracks
        tracks.add(track("us-1", "Campus orientation",  "american", 1, "Prestructural", "2:50", "Education", 4));
        tracks.add(track("us-2", "Radio traffic update","american", 2, "Unistructural",  "2:20", "Transport", 4));

        return tracks;
    }

    private Map<String, Object> track(String id, String title, String accent,
                                      int level, String levelName,
                                      String duration, String topic, int qCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",         id);
        m.put("title",      title);
        m.put("accent",     accent);
        m.put("level",      level);
        m.put("levelName",  levelName);
        m.put("duration",   duration);
        m.put("topic",      topic);
        m.put("questionCount", qCount);
        return m;
    }



}