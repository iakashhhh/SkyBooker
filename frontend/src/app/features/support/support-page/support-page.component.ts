import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { SupportApiService } from '../../../core/services/support-api.service';

interface SupportPreset {
  label: string;
  category: string;
  subject: string;
  message: string;
}

@Component({
  selector: 'app-support-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './support-page.component.html',
  styleUrl: './support-page.component.css'
})
export class SupportPageComponent {
  readonly categories = [
    'Booking Issue',
    'Payment Issue',
    'Flight Search Issue',
    'Cancellation / Refund',
    'PNR / Ticket Issue',
    'Account / Login',
    'Other'
  ];

  readonly presets: SupportPreset[] = [
    {
      label: 'Payment done, booking not confirmed',
      category: 'Payment Issue',
      subject: 'Payment completed but booking still pending',
      message: 'I completed payment, but my booking status is still pending. Please verify and confirm the booking.'
    },
    {
      label: 'Unable to find flights for valid route',
      category: 'Flight Search Issue',
      subject: 'Flight search shows no data',
      message: 'Flight search is returning no flights for a route/date where flights should be available.'
    },
    {
      label: 'Need refund update',
      category: 'Cancellation / Refund',
      subject: 'Refund status request',
      message: 'I need an update on my cancellation/refund status. Please share current progress and expected timeline.'
    },
    {
      label: 'PNR/ticket mismatch in email',
      category: 'PNR / Ticket Issue',
      subject: 'Ticket details mismatch',
      message: 'The ticket shown in app and the email ticket details do not match. Please investigate and send corrected ticket details.'
    }
  ];

  readonly form = this.formBuilder.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    bookingId: [''],
    category: [this.categories[0], Validators.required],
    subject: ['', [Validators.required, Validators.minLength(6)]],
    message: ['', [Validators.required, Validators.minLength(20)]]
  });

  isSubmitting = false;
  submitError = '';
  submitSuccess = '';

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly supportApiService: SupportApiService
  ) {}

  applyPreset(preset: SupportPreset): void {
    this.form.patchValue({
      category: preset.category,
      subject: preset.subject,
      message: preset.message
    });
    this.submitError = '';
    this.submitSuccess = '';
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.submitError = '';
    this.submitSuccess = '';

    const payload = this.form.getRawValue();
    this.supportApiService.submitInquiry({
      fullName: String(payload.fullName ?? '').trim(),
      email: String(payload.email ?? '').trim(),
      phone: String(payload.phone ?? '').trim() || undefined,
      bookingId: String(payload.bookingId ?? '').trim() || undefined,
      category: String(payload.category ?? '').trim(),
      subject: String(payload.subject ?? '').trim(),
      message: String(payload.message ?? '').trim()
    })
      .pipe(finalize(() => {
        this.isSubmitting = false;
      }))
      .subscribe({
        next: (response) => {
          this.submitSuccess = `${response.message} Reference: ${response.ticketRef}`;
          this.form.patchValue({
            category: this.categories[0],
            subject: '',
            message: '',
            bookingId: ''
          });
          this.form.controls.subject.markAsPristine();
          this.form.controls.message.markAsPristine();
          this.form.controls.bookingId.markAsPristine();
        },
        error: (error) => {
          this.submitError = error?.error?.message ?? 'Unable to submit your query right now. Please try again.';
        }
      });
  }
}
