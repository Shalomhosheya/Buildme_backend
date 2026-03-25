package com.buildme.repository;

import com.buildme.model.QuizAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface QuizAttemptRepository extends MongoRepository<QuizAttempt, String> {
    List<QuizAttempt> findByUserIdOrderByCompletedAtDesc(String userId);
    List<QuizAttempt> findByUserIdAndSkill(String userId, String skill);
    boolean existsByUserIdAndQuizId(String userId, String quizId);
    long countByUserId(String userId);
}
