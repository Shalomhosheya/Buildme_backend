package com.buildme.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;



// ── Auth DTOs ──────────────────────────────────────────────────────────────

@Data
class RegisterRequest {
    @NotBlank @Email
    public String email;
    @NotBlank @Size(min = 6)
    public String password;
    @NotBlank
    public String name;
}

@Data
class LoginRequest {
    @NotBlank @Email
    public String email;
    @NotBlank
    public String password;
}

@Data
class AuthResponse {
    public String token;
    public String userId;
    public String name;
    public String email;

    public AuthResponse(String token, String userId, String name, String email) {
        this.token = token; this.userId = userId;
        this.name = name;   this.email = email;
    }
}

// ── Note DTOs ──────────────────────────────────────────────────────────────

@Data
class NoteRequest {
    @NotBlank public String title;
    public String content;
    public String tag;
}

// ── Quiz DTOs ──────────────────────────────────────────────────────────────

@Data
class QuizSubmitRequest {
    @NotBlank public String quizId;
    @NotBlank public String skill;
    public int soloLevel;
    public int score;
    public int totalQuestions;
}

// ── AI Tutor DTOs ──────────────────────────────────────────────────────────

@Data
class EssayRequest {
    public String question;
    @NotBlank public String essay;
    public String taskType;
}

// ── Points DTOs ────────────────────────────────────────────────────────────

@Data
class AddPointsRequest {
    @NotBlank public String skill;
    public int points;
    public String reason;
}
