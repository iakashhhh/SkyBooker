package com.skybooker.authservice.security;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This service keeps a simple in-memory blacklist for JWT logout support.
 * Blacklisted tokens are rejected by JWT filter during request processing.
 */
@Service
public class TokenBlacklistService {

    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    /**
     * Adds a token into blacklist set.
     */
    public void blacklistToken(String token) {
        blacklistedTokens.add(token);
    }

    /**
     * Checks whether token is already blacklisted.
     */
    public boolean isBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }
}
