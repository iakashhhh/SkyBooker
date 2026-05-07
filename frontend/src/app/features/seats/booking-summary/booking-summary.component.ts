import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { Seat } from '../../../core/models/seat.models';

@Component({
  selector: 'app-booking-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './booking-summary.component.html',
  styleUrl: './booking-summary.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingSummaryComponent {
  @Input() selectedSeats: Seat[] = [];
  @Input() baseFare = 0;
  @Input() countdownSeconds = 0;

  @Output() releaseAll = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();

  get passengerCount(): number {
    return this.selectedSeats.length;
  }

  get estimatedFare(): number {
    if (this.baseFare <= 0 || !this.selectedSeats.length) {
      return 0;
    }

    return this.selectedSeats.reduce((sum, seat) => sum + this.estimateSeatFare(seat), 0);
  }

  get baseFareAmount(): number {
    return Math.max(this.baseFare, 0) * this.passengerCount;
  }

  get seatCharges(): number {
    return Math.max(this.estimatedFare - this.baseFareAmount, 0);
  }

  private estimateSeatFare(seat: Seat): number {
    const seatCharge = this.resolveSeatCharge(seat);
    const classMultiplierEffect = this.resolveClassMultiplierEffect(seat);
    return this.baseFare + seatCharge + classMultiplierEffect;
  }

  private resolveSeatCharge(seat: Seat): number {
    let charge = 0;
    if (seat.isWindow) {
      charge += 200;
    }
    if (seat.hasExtraLegroom) {
      charge += 500;
    }
    return charge;
  }

  private resolveClassMultiplierEffect(seat: Seat): number {
    switch (seat.seatClass) {
      case 'BUSINESS':
        return this.baseFare;
      case 'FIRST':
        return this.baseFare * 2;
      case 'ECONOMY':
      default:
        return 0;
    }
  }

  get countdownText(): string {
    const safe = Math.max(this.countdownSeconds, 0);
    const minutes = Math.floor(safe / 60)
      .toString()
      .padStart(2, '0');
    const seconds = (safe % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  }
}
