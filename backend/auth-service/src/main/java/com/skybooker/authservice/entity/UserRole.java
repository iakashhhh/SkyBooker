package com.skybooker.authservice.entity;

/**
 * This enum contains all supported roles in the auth service.
 * These roles are used for authorization checks in secured endpoints.
 */
public enum UserRole {
    PASSENGER,
    ADMIN,
    AIRLINE_STAFF
}
