import { Injectable } from '@angular/core';

/**
 * This service manages JWT token and role in browser local storage.
 * A dedicated utility class keeps token handling centralized.
 */
@Injectable({
  providedIn: 'root'
})
export class TokenStorageService {
  private readonly tokenKey = 'skybooker_token';
  private readonly roleKey = 'skybooker_role';
  private readonly userIdKey = 'skybooker_user_id';

  saveToken(token: string): void {
    localStorage.setItem(this.tokenKey, token);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  saveRole(role: string): void {
    const normalizedRole = this.normalizeRole(role);
    localStorage.setItem(this.roleKey, normalizedRole);
  }

  getRole(): string | null {
    const storedRole = localStorage.getItem(this.roleKey);
    if (!storedRole) {
      return null;
    }
    return this.normalizeRole(storedRole);
  }

  saveUserId(userId: number): void {
    localStorage.setItem(this.userIdKey, String(userId));
  }

  getUserId(): number | null {
    const raw = localStorage.getItem(this.userIdKey);
    if (!raw) {
      return null;
    }

    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }

  clear(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.roleKey);
    localStorage.removeItem(this.userIdKey);
  }

  private normalizeRole(role: string | null | undefined): string {
    const value = String(role ?? '').trim().toUpperCase();
    if (value === 'ROLE_ADMIN' || value === 'ADMIN') {
      return 'ADMIN';
    }
    if (value === 'ROLE_AIRLINE_STAFF' || value === 'AIRLINE_STAFF') {
      return 'AIRLINE_STAFF';
    }
    if (value === 'ROLE_PASSENGER' || value === 'PASSENGER') {
      return 'PASSENGER';
    }
    return value;
  }
}
