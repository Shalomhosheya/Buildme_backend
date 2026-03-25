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
@Document(collection = "essay_evaluations")
public class EssayEvaluation {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String question;
    private String essay;
    private String taskType;

    private double overallBand;
    private double taskResponse;
    private double coherenceCohesion;
    private double lexicalResource;
    private double grammaticalRange;

    private String feedback;
    private int pointsEarned;

    @CreatedDate
    private LocalDateTime evaluatedAt;
}
