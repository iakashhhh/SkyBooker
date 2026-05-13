package com.skybooker.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSchemaMigrationTest {

    @Test
    void shouldRunBothAlterStatementsWhenColumnsNeedMigration() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
            .thenReturn("enum")
            .thenReturn("NO");

        new NotificationSchemaMigration(jdbcTemplate);

        verify(jdbcTemplate, times(2)).execute(anyString());
    }

    @Test
    void shouldSkipAlterWhenColumnsAlreadyCompatible() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
            .thenReturn("varchar")
            .thenReturn("YES");

        new NotificationSchemaMigration(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void shouldIgnoreMigrationExceptions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("information schema unavailable"));

        assertDoesNotThrow(() -> new NotificationSchemaMigration(jdbcTemplate));
    }
}
