package com.skybooker.authservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "bXktc3VwZXItc2VjcmV0LWtleS1teS1zdXBlci1zZWNyZXQta2V5LTEyMzQ1Ng==");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 60_000L);
    }

    @Test
    void shouldGenerateAndParseTokenClaims() {
        String token = jwtService.generateToken("user@skybooker.com", "PASSENGER");

        assertEquals("user@skybooker.com", jwtService.extractEmail(token));
        assertEquals("PASSENGER", jwtService.extractRole(token));
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void shouldReturnFalseForMalformedToken() {
        assertFalse(jwtService.isTokenValid("not-a-jwt"));
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -10L);
        String expiredToken = jwtService.generateToken("expired@skybooker.com", "PASSENGER");

        assertFalse(jwtService.isTokenValid(expiredToken));
    }
}
