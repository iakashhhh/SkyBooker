package com.skybooker.authservice.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBlacklistServiceTest {

    @Test
    void shouldTrackBlacklistedTokens() {
        TokenBlacklistService service = new TokenBlacklistService();

        assertFalse(service.isBlacklisted("token-1"));
        service.blacklistToken("token-1");
        assertTrue(service.isBlacklisted("token-1"));
        assertFalse(service.isBlacklisted("token-2"));
    }
}
