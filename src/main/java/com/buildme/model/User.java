package com.buildme.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String name;

    @Builder.Default
    private int totalPoints = 0;

    @Builder.Default
    private int streakDays = 0;

    @Builder.Default
    private double estimatedBandScore = 0.0;

    @Builder.Default
    private List<String> earnedBadges = new ArrayList<>();

    @Builder.Default
    private SkillProgress writing   = new SkillProgress("Writing");
    @Builder.Default
    private SkillProgress reading   = new SkillProgress("Reading");
    @Builder.Default
    private SkillProgress listening = new SkillProgress("Listening");
    @Builder.Default
    private SkillProgress speaking  = new SkillProgress("Speaking");

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @Field("avatarUrl")
    private String avatarUrl;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillProgress {
        private String name;
        private int level = 1;
        private String levelName = "Prestructural";
        private int points = 0;
        private int maxPoints = 300;

        public SkillProgress(String name) {
            this.name = name;
        }
    }
}
