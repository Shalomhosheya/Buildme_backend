package com.buildme.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "listening_attempts")
@Data
public  class ListeningAttempt {
    @Id
    private String id;
    private String  userId;
    private String  trackId;
    private String  accent;
    private int     score;
    private int     total;
    private int     pointsEarned;
    private boolean passed;
    @CreatedDate
    private LocalDateTime completedAt;
}