package com.buildme.service;

import com.buildme.model.User;
import com.buildme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SkillProgressService {

    private final UserRepository userRepository;

    private static final int[] LEVEL_THRESHOLDS = {0, 300, 700, 1200};
    private static final String[] LEVEL_NAMES = {"Prestructural", "Unistructural", "Multistructural"};

    public User addSkillPoints(String userId, String skill, int points) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTotalPoints(user.getTotalPoints() + points);

        User.SkillProgress sp = getSkillProgress(user, skill);
        if (sp != null) {
            int newPts = sp.getPoints() + points;
            sp.setPoints(newPts);
            sp.setLevel(calculateLevel(newPts));
            sp.setLevelName(getLevelName(sp.getLevel()));
            sp.setMaxPoints(getMaxPoints(sp.getLevel()));
        }

        return user;
    }

    private User.SkillProgress getSkillProgress(User user, String skill) {
        return switch (skill.toLowerCase()) {
            case "writing"   -> user.getWriting();
            case "reading"   -> user.getReading();
            case "listening" -> user.getListening();
            case "speaking"  -> user.getSpeaking();
            default          -> null;
        };
    }

    private int calculateLevel(int points) {
        if (points >= LEVEL_THRESHOLDS[3]) return 3;
        if (points >= LEVEL_THRESHOLDS[2]) return 3;
        if (points >= LEVEL_THRESHOLDS[1]) return 2;
        return 1;
    }

    private String getLevelName(int level) {
        return switch (level) {
            case 1  -> "Prestructural";
            case 2  -> "Unistructural";
            case 3  -> "Multistructural";
            default -> "Prestructural";
        };
    }

    private int getMaxPoints(int level) {
        return switch (level) {
            case 1  -> 300;
            case 2  -> 700;
            case 3  -> 1200;
            default -> 300;
        };
    }
}
