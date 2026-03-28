package com.buildme.repository;


import com.buildme.model.SpeakingEval;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
public interface SpeakingEvalRepository extends MongoRepository<SpeakingEval, String> {
    List<SpeakingEval> findByUserIdOrderByEvaluatedAtDesc(String userId);
}