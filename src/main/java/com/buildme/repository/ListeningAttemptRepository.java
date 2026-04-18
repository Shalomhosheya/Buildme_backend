package com.buildme.repository;

import com.buildme.model.ListeningAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ListeningAttemptRepository extends MongoRepository<ListeningAttempt, String> {
    List<ListeningAttempt> findByUserIdOrderByCompletedAtDesc(String userId);
}