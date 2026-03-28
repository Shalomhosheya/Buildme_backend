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
@Document(collection = "speaking_evaluations")
public class SpeakingEval {
        @Id
        private String id;
        private String userId;
        private String question;
        private String transcript;
        private double fluency;
        private double pronunciation;
        private double vocabulary;
        private double grammar;
        private double overallBand;
        private String feedback;
        private List<String> improvements;
        private int pointsEarned;
        @CreatedDate
        private LocalDateTime evaluatedAt;

}
