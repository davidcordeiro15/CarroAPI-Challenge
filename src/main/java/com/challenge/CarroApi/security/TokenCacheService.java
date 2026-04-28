package com.challenge.CarroApi.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenCacheService {

    private static class CachedToken {
        String email;
        String role;
        long expiresAt;
    }

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    private final long TTL = 5 * 60 * 1000; // 5 minutos

    public void save(String token, String email, String role) {
        CachedToken ct = new CachedToken();
        ct.email = email;
        ct.role = role;
        ct.expiresAt = Instant.now().toEpochMilli() + TTL;

        cache.put(token, ct);
    }

    public CachedToken get(String token) {
        CachedToken ct = cache.get(token);

        if (ct == null) return null;

        if (Instant.now().toEpochMilli() > ct.expiresAt) {
            cache.remove(token);
            return null;
        }

        return ct;
    }
}