package com.skybooker.notificationservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("entityManagerFactory")
public class NotificationSchemaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSchemaMigration.class);

    public NotificationSchemaMigration(JdbcTemplate jdbcTemplate) {
        try {
            migrateNotificationTypeColumn(jdbcTemplate);
            makeRelatedBookingIdNullable(jdbcTemplate);
        } catch (Exception exception) {
            LOGGER.warn("Notification schema migration skipped: {}", exception.getMessage());
        }
    }

    private void migrateNotificationTypeColumn(JdbcTemplate jdbcTemplate) {
        String dataType = jdbcTemplate.queryForObject(
            """
                SELECT DATA_TYPE
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'notifications'
                  AND column_name = 'type'
                LIMIT 1
                """,
            String.class
        );

        if (dataType != null && "enum".equalsIgnoreCase(dataType)) {
            jdbcTemplate.execute("ALTER TABLE notifications MODIFY COLUMN type VARCHAR(32) NOT NULL");
            LOGGER.info("Migrated notifications.type from ENUM to VARCHAR(32)");
        }
    }

    private void makeRelatedBookingIdNullable(JdbcTemplate jdbcTemplate) {
        String nullable = jdbcTemplate.queryForObject(
            """
                SELECT IS_NULLABLE
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'notifications'
                  AND column_name = 'related_booking_id'
                LIMIT 1
                """,
            String.class
        );

        if (nullable != null && "NO".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("ALTER TABLE notifications MODIFY COLUMN related_booking_id VARCHAR(255) NULL");
            LOGGER.info("Updated notifications.related_booking_id to allow NULL values");
        }
    }
}
