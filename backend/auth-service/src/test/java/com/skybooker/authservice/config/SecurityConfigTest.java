package com.skybooker.authservice.config;

import com.skybooker.authservice.security.CustomUserDetailsService;
import com.skybooker.authservice.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void shouldProvidePasswordEncoderAndAuthenticationProvider() {
        JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        SecurityConfig config = new SecurityConfig(filter, userDetailsService);

        PasswordEncoder encoder = config.passwordEncoder();
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(
            User.withUsername("user@test.com")
                .password(encoder.encode("pass123"))
                .roles("PASSENGER")
                .build()
        );

        AuthenticationProvider provider = config.authenticationProvider();
        var result = provider.authenticate(new UsernamePasswordAuthenticationToken("user@test.com", "pass123"));

        assertNotNull(encoder);
        assertTrue(result.isAuthenticated());
        assertEquals("user@test.com", ((UserDetails) result.getPrincipal()).getUsername());
    }

    @Test
    void shouldReturnAuthenticationManagerFromConfiguration() throws Exception {
        JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        SecurityConfig config = new SecurityConfig(filter, userDetailsService);

        AuthenticationManager manager = mock(AuthenticationManager.class);
        AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
        when(configuration.getAuthenticationManager()).thenReturn(manager);

        assertSame(manager, config.authenticationManager(configuration));
    }
}
