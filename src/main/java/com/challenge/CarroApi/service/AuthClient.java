package com.challenge.CarroApi.service;

import com.challenge.CarroApi.dto.TokenValidationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthClient {

    private final RestTemplate restTemplate;

    @Value("${auth.api.url}")
    private String authApiUrl;

    public AuthClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public TokenValidationResponse validateToken(String token) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"token\":\"" + token + "\"}";

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<TokenValidationResponse> response =
                restTemplate.exchange(
                        authApiUrl + "/auth/validate",
                        HttpMethod.POST,
                        entity,
                        TokenValidationResponse.class
                );

        return response.getBody();
    }
}