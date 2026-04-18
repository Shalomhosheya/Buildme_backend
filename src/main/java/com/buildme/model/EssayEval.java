package com.buildme.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "essay_evaluations_v2")
public class EssayEval {
    @Id
    private String id;
    private String userId, essay, question, taskType;
    private double overallBand;
    private int wordCount, pointsEarned;
    @CreatedDate
    private LocalDateTime evaluatedAt;
}