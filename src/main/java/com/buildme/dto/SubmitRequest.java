package com.buildme.dto;

import lombok.Data;

@Data
public class SubmitRequest {
    public String trackId;
    public String accent;
    public int score;
    public int total;
}
