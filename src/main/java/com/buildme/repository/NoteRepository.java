package com.buildme.repository;

import com.buildme.model.Note;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends MongoRepository<Note, String> {
    List<Note> findByUserIdOrderByUpdatedAtDesc(String userId);
    List<Note> findByUserIdAndTagOrderByUpdatedAtDesc(String userId, String tag);
    Optional<Note> findByIdAndUserId(String id, String userId);
    void deleteByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
}
