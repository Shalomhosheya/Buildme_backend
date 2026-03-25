package com.buildme.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "quiz_attempts")
public class QuizAttempt {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String quizId;
    private String skill;
    private int soloLevel;
    private int score;
    private int totalQuestions;
    private int pointsEarned;
    private boolean passed;

    @CreatedDate
    private LocalDateTime completedAt;
}
