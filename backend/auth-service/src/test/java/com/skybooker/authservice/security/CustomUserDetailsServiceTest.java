package com.skybooker.authservice.security;

import com.skybooker.authservice.entity.User;
import com.skybooker.authservice.entity.UserRole;
import com.skybooker.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(userRepository);
    }

    @Test
    void shouldLoadActiveUserWithRoleAuthority() {
        User user = new User();
        user.setEmail("staff@skybooker.com");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.AIRLINE_STAFF);
        user.setActive(true);

        when(userRepository.findByEmailIgnoreCase("staff@skybooker.com")).thenReturn(Optional.of(user));

        var loaded = customUserDetailsService.loadUserByUsername("staff@skybooker.com");

        assertEquals("staff@skybooker.com", loaded.getUsername());
        assertEquals("encoded", loaded.getPassword());
        assertTrue(loaded.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AIRLINE_STAFF")));
    }

    @Test
    void shouldThrowWhenUserIsMissing() {
        when(userRepository.findByEmailIgnoreCase("missing@skybooker.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> customUserDetailsService.loadUserByUsername("missing@skybooker.com"));
    }

    @Test
    void shouldThrowWhenUserIsInactive() {
        User user = new User();
        user.setEmail("inactive@skybooker.com");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.PASSENGER);
        user.setActive(false);

        when(userRepository.findByEmailIgnoreCase("inactive@skybooker.com")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class,
            () -> customUserDetailsService.loadUserByUsername("inactive@skybooker.com"));
    }
}
