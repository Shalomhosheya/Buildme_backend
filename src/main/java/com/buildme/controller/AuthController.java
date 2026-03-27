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
    public ResponseEntity<?> me(Authentication authentication) {

        // 🔐 Get userId from Spring Security context
        String userId = (String) authentication.getPrincipal();

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized"));
        }

        return userRepository.findById(userId)
                .map(u -> {

                    // ✅ Use HashMap (safe for null values)
                    Map<String, Object> response = new HashMap<>();

                    response.put("id", u.getId());
                    response.put("name", u.getName());
                    response.put("email", u.getEmail());
                    response.put("totalPoints", u.getTotalPoints());
                    response.put("streakDays", u.getStreakDays());
                    response.put("estimatedBandScore", u.getEstimatedBandScore());
                    response.put("earnedBadges", u.getEarnedBadges());

                    response.put("writing", u.getWriting() != null ? u.getWriting() : "N/A");
                    response.put("reading", u.getReading() != null ? u.getReading() : "N/A");
                    response.put("listening", u.getListening() != null ? u.getListening() : "N/A");
                    response.put("speaking", u.getSpeaking() != null ? u.getSpeaking() : "N/A");

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found")));
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
