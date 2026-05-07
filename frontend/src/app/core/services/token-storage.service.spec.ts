import { TestBed } from '@angular/core/testing';

import { TokenStorageService } from './token-storage.service';

describe('TokenStorageService', () => {
  let service: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TokenStorageService]
    });
    service = TestBed.inject(TokenStorageService);
    localStorage.clear();
  });

  it('stores and reads token/role/user id with role normalization', () => {
    service.saveToken('jwt');
    service.saveRole('role_airline_staff');
    service.saveUserId(21);

    expect(service.getToken()).toBe('jwt');
    expect(service.getRole()).toBe('AIRLINE_STAFF');
    expect(service.getUserId()).toBe(21);
  });

  it('returns null for missing or invalid user id', () => {
    expect(service.getUserId()).toBeNull();

    localStorage.setItem('skybooker_user_id', 'NaN');
    expect(service.getUserId()).toBeNull();

    localStorage.setItem('skybooker_user_id', '-5');
    expect(service.getUserId()).toBeNull();
  });

  it('clears all auth keys', () => {
    localStorage.setItem('skybooker_token', 'jwt');
    localStorage.setItem('skybooker_role', 'ADMIN');
    localStorage.setItem('skybooker_user_id', '1');

    service.clear();

    expect(localStorage.getItem('skybooker_token')).toBeNull();
    expect(localStorage.getItem('skybooker_role')).toBeNull();
    expect(localStorage.getItem('skybooker_user_id')).toBeNull();
  });
});
