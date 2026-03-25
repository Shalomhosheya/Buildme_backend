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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    public ResponseEntity<?> me(@RequestAttribute("userId") String userId) {

        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(Map.ofEntries(
                        Map.entry("id", u.getId()),
                        Map.entry("name", u.getName()),
                        Map.entry("email", u.getEmail()),
                        Map.entry("totalPoints", u.getTotalPoints()),
                        Map.entry("streakDays", u.getStreakDays()),
                        Map.entry("estimatedBandScore", u.getEstimatedBandScore()),
                        Map.entry("earnedBadges", u.getEarnedBadges()),
                        Map.entry("writing", u.getWriting() != null ? u.getWriting() : "N/A"),
                        Map.entry("reading", u.getReading() != null ? u.getReading() : "N/A"),
                        Map.entry("listening", u.getListening() != null ? u.getListening() : "N/A"),
                        Map.entry("speaking", u.getSpeaking() != null ? u.getSpeaking() : "N/A")
                )))
                .orElse(ResponseEntity.notFound().build());
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
