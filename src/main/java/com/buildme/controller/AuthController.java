package com.buildme.controller;

import com.buildme.model.User;
import com.buildme.repository.UserRepository;
import com.buildme.security.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Email already registered"));
        }

        User user = User.builder()
                .email(req.email)
                .password(passwordEncoder.encode(req.password))
                .name(req.name)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("token", token, "userId", user.getId(),
                       "name", user.getName(), "email", user.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        return userRepository.findByEmail(req.email)
                .filter(u -> passwordEncoder.matches(req.password, u.getPassword()))
                .map(user -> {
                    user.setLastLoginAt(LocalDateTime.now());
                    userRepository.save(user);
                    String token = jwtUtil.generateToken(user.getId(), user.getEmail());
                    return ResponseEntity.ok(Map.of(
                            "token", token, "userId", user.getId(),
                            "name", user.getName(), "email", user.getEmail()));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid email or password")));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        String userId = auth.getName();
        return userRepository.findById(userId).map(u -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("id",                 u.getId());
            resp.put("name",               u.getName());
            resp.put("email",              u.getEmail());
            resp.put("totalPoints",        u.getTotalPoints());
            resp.put("streakDays",         u.getStreakDays());
            resp.put("estimatedBandScore", u.getEstimatedBandScore());
            resp.put("earnedBadges",       u.getEarnedBadges());
            resp.put("writing",            u.getWriting());
            resp.put("reading",            u.getReading());
            resp.put("listening",          u.getListening());
            resp.put("speaking",           u.getSpeaking());
            resp.put("avatarUrl",          u.getAvatarUrl());
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.notFound().build());
    }

    @Data static class RegisterRequest {
        @NotBlank @Email public String email;
        @NotBlank @Size(min = 6) public String password;
        @NotBlank public String name;
    }

    @Data static class LoginRequest {
        @NotBlank @Email public String email;
        @NotBlank public String password;
    }
}
