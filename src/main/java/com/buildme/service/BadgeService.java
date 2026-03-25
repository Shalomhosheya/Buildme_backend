package com.buildme.service;

import com.buildme.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BadgeService {

    private static final String STARTER   = "Starter";
    private static final String EXPLORER  = "Explorer";
    private static final String BUILDER   = "Builder";
    private static final String ANALYST   = "Analyst";
    private static final String CERTIFIED = "Certified";
    private static final String READY     = "IELTS Ready";

    public void checkAndAwardBadges(User user) {
        List<String> badges = user.getEarnedBadges();
        if (badges == null) {
            badges = new ArrayList<>();
            user.setEarnedBadges(badges);
        }

        // Starter — any skill reaches Level 1 completion (50+ pts)
        if (!badges.contains(STARTER) && hasAnySkillPoints(user, 50)) {
            badges.add(STARTER);
        }

        // Explorer — any skill reaches Level 2 (150+ pts)
        if (!badges.contains(EXPLORER) && hasAnySkillPoints(user, 150)) {
            badges.add(EXPLORER);
        }

        // Builder — Writing reaches Level 3 (350+ pts)
        if (!badges.contains(BUILDER) && user.getWriting().getPoints() >= 350) {
            badges.add(BUILDER);
        }

        // Analyst — all skills reach Level 3 (350+ pts each)
        if (!badges.contains(ANALYST) && allSkillsAtLevel3(user)) {
            badges.add(ANALYST);
        }

        // IELTS Ready — estimated band >= 7.0
        if (!badges.contains(READY) && user.getEstimatedBandScore() >= 7.0) {
            badges.add(READY);
        }

        // Certified badge awarded separately by CertificateController
    }

    private boolean hasAnySkillPoints(User user, int minPts) {
        return user.getWriting().getPoints()   >= minPts
            || user.getReading().getPoints()   >= minPts
            || user.getListening().getPoints() >= minPts
            || user.getSpeaking().getPoints()  >= minPts;
    }

    private boolean allSkillsAtLevel3(User user) {
        return user.getWriting().getPoints()   >= 350
            && user.getReading().getPoints()   >= 350
            && user.getListening().getPoints() >= 350
            && user.getSpeaking().getPoints()  >= 350;
    }
}
