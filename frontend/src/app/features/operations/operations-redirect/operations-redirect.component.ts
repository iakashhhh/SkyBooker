import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

import { TokenStorageService } from '../../../core/services/token-storage.service';

@Component({
  selector: 'app-operations-redirect',
  standalone: true,
  template: ''
})
export class OperationsRedirectComponent implements OnInit {
  constructor(
    private readonly tokenStorageService: TokenStorageService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const role = this.tokenStorageService.getRole();
    if (role === 'ADMIN') {
      this.router.navigateByUrl('/admin/dashboard');
      return;
    }
    if (role === 'AIRLINE_STAFF') {
      this.router.navigateByUrl('/airline/dashboard');
      return;
    }
    this.router.navigateByUrl('/');
  }
}

