package com.buildme.repository;

import com.buildme.controller.EssayController;
import com.buildme.model.EssayEval;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EssayEvalRepo extends MongoRepository<EssayController.EssayEval, String> {
    List<EssayController.EssayEval> findByUserIdOrderByEvaluatedAtDesc(String userId);
}