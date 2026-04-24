package com.buildme.controller;

import com.buildme.model.User;
import com.buildme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final UserRepository userRepository;

    @Value("${app.upload.dir:uploads/avatars}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final long        MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED  = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    // ── POST /api/profile/avatar ───────────────────────────────────────────
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            Authentication auth) {

        try {
            log.info("Avatar upload request — user={} size={} type={}",
                    auth.getName(), file.getSize(), file.getContentType());

            // ── Validate ──
            if (file.isEmpty())
                return bad("No file received — please select an image.");

            if (file.getSize() > MAX_SIZE)
                return bad("File is too large. Maximum allowed size is 5 MB.");

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED.contains(contentType))
                return bad("File type not allowed. Please upload a JPEG, PNG, WebP or GIF image.");

            String userId = auth.getName();

            // ── Delete old avatar file ──
            userRepository.findById(userId).ifPresent(u -> {
                if (u.getAvatarUrl() != null) {
                    try {
                        Path old = Paths.get(".", u.getAvatarUrl());
                        Files.deleteIfExists(old);
                        log.info("Deleted old avatar: {}", old);
                    } catch (IOException e) {
                        log.warn("Could not delete old avatar: {}", e.getMessage());
                    }
                }
            });

            // ── Create upload directory ──
            Path dir = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            log.info("Upload directory: {}", dir);

            // ── Build filename ──
            String ext      = getExtension(Objects.toString(file.getOriginalFilename(), "file.jpg"));
            String filename = userId + "-" + System.currentTimeMillis() + "." + ext;
            Path   dest     = dir.resolve(filename);

            // ── Write file ──
            file.transferTo(dest.toFile());
            log.info("Saved avatar to: {}", dest);

            // ── Persist relative path in MongoDB ──
            String relPath = "/uploads/avatars/" + filename;
            userRepository.findById(userId).ifPresent(u -> {
                u.setAvatarUrl(relPath);
                userRepository.save(u);
                log.info("Updated avatarUrl for user {}: {}", userId, relPath);
            });

            // ── Return full URL ──
            String fullUrl = baseUrl + relPath;
            Map<String, Object> resp = new HashMap<>();
            resp.put("avatarUrl", fullUrl);
            resp.put("message",   "Profile photo updated successfully");
            return ResponseEntity.ok(resp);

        } catch (IOException e) {
            log.error("IO error saving avatar: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Could not save file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in avatar upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    // ── DELETE /api/profile/avatar ─────────────────────────────────────────
    @DeleteMapping("/avatar")
    public ResponseEntity<?> deleteAvatar(Authentication auth) {
        try {
            String userId = auth.getName();
            userRepository.findById(userId).ifPresent(u -> {
                if (u.getAvatarUrl() != null) {
                    try {
                        Files.deleteIfExists(Paths.get(".", u.getAvatarUrl()));
                    } catch (IOException e) {
                        log.warn("Could not delete avatar file: {}", e.getMessage());
                    }
                    u.setAvatarUrl(null);
                    userRepository.save(u);
                }
            });
            return ResponseEntity.ok(Map.of("message", "Profile photo removed"));
        } catch (Exception e) {
            log.error("Error deleting avatar: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to remove photo: " + e.getMessage()));
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────
    private ResponseEntity<?> bad(String msg) {
        log.warn("Avatar upload rejected: {}", msg);
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1).toLowerCase() : "jpg";
    }
}