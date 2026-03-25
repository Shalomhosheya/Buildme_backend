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
@Document(collection = "certificates")
public class Certificate {

    @Id
    private String id;

    @Indexed(unique = true)
    private String certId;

    @Indexed
    private String userId;

    private String userName;
    private double estimatedBand;
    private int totalPoints;
    private int quizzesCompleted;
    private boolean verified;

    @CreatedDate
    private LocalDateTime issuedAt;
}
