package com.buildme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;

@Configuration
@EnableMongoAuditing
public class AppConfig {

    @Value("${app.ai.timeout:30000}")
    private int aiTimeout;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(aiTimeout))
                .setReadTimeout(Duration.ofMillis(aiTimeout))
                .build();
    }
}
