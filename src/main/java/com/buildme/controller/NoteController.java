package com.buildme.controller;

import com.buildme.model.Note;
import com.buildme.repository.NoteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteRepository noteRepository;

    @GetMapping
    public ResponseEntity<List<Note>> getAllNotes(
            @RequestParam(required = false) String tag,
            Authentication auth) {
        String userId = auth.getName();
        List<Note> notes = (tag != null && !tag.isBlank())
                ? noteRepository.findByUserIdAndTagOrderByUpdatedAtDesc(userId, tag)
                : noteRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return ResponseEntity.ok(notes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Note> getNote(@PathVariable String id, Authentication auth) {
        return noteRepository.findByIdAndUserId(id, auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Note> createNote(@Valid @RequestBody NoteRequest req,
                                           Authentication auth) {
        Note note = Note.builder()
                .userId(auth.getName())
                .title(req.title)
                .content(req.content != null ? req.content : "")
                .tag(req.tag != null ? req.tag : "general")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(noteRepository.save(note));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Note> updateNote(@PathVariable String id,
                                           @Valid @RequestBody NoteRequest req,
                                           Authentication auth) {
        return noteRepository.findByIdAndUserId(id, auth.getName())
                .map(note -> {
                    note.setTitle(req.title);
                    note.setContent(req.content != null ? req.content : note.getContent());
                    note.setTag(req.tag != null ? req.tag : note.getTag());
                    return ResponseEntity.ok(noteRepository.save(note));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNote(@PathVariable String id,
                                                          Authentication auth) {
        return noteRepository.findByIdAndUserId(id, auth.getName())
                .map(note -> {
                    noteRepository.delete(note);
                    return ResponseEntity.ok(Map.of("message", "Note deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countNotes(Authentication auth) {
        long count = noteRepository.countByUserId(auth.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Data static class NoteRequest {
        @NotBlank public String title;
        public String content;
        public String tag;
    }
}
