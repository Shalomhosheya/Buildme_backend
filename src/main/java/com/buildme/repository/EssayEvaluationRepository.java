package com.buildme.repository;

import com.buildme.model.EssayEvaluation;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EssayEvaluationRepository extends MongoRepository<EssayEvaluation, String> {
    List<EssayEvaluation> findByUserIdOrderByEvaluatedAtDesc(String userId);
    long countByUserId(String userId);
}
