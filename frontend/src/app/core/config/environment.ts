/**
 * This file stores frontend environment values.
 * API base URL points to API Gateway, not directly to microservices.
 */
export const environment = {
  apiBaseUrl: (globalThis as { __env?: { apiBaseUrl?: string } }).__env?.apiBaseUrl ?? 'http://localhost:8080',
  googleClientId: (globalThis as { __env?: { googleClientId?: string } }).__env?.googleClientId ?? ''
};
