package com.challenge.CarroApi.service;

import com.challenge.CarroApi.service.AuthClient;
import com.challenge.CarroApi.dto.TokenValidationResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenValidationService {

    private final AuthClient authClient;

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    private static final long TTL_SECONDS = 300;

    public TokenValidationService(AuthClient authClient) {
        this.authClient = authClient;
    }

    public TokenValidationResponse validate(String token) {

        CachedToken cached = cache.get(token);

        if (cached != null && !cached.isExpired()) {
            return cached.response;
        }

        TokenValidationResponse response = authClient.validateToken(token);

        if (response != null && response.valid()) {
            cache.put(token, new CachedToken(response));
        }

        return response;
    }

    static class CachedToken {
        TokenValidationResponse response;
        Instant createdAt;

        public CachedToken(TokenValidationResponse response) {
            this.response = response;
            this.createdAt = Instant.now();
        }

        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(TTL_SECONDS));
        }
    }
}