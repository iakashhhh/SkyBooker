import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { trigger, style, animate, transition } from '@angular/animations';

import { Seat, SeatClass } from '../../../core/models/seat.models';

interface SeatRow {
  rowNumber: number;
  leftSeats: Seat[];
  middleSeats: Seat[];
  rightSeats: Seat[];
  hasExtraLegroom: boolean;
}

interface CabinSection {
  seatClass: SeatClass;
  leftLabels: string[];
  middleLabels: string[];
  rightLabels: string[];
  rows: SeatRow[];
}

interface BlockSize {
  left: number;
  middle: number;
  right: number;
}

@Component({
  selector: 'app-seat-map',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './seat-map.component.html',
  styleUrl: './seat-map.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger('selectPulse', [
      transition(':enter', [
        style({ transform: 'scale(0)', opacity: 0.9 }),
        animate('180ms cubic-bezier(0.16, 1, 0.3, 1)', style({ transform: 'scale(1)', opacity: 0 }))
      ])
    ])
  ]
})
export class SeatMapComponent {
  @Input() seats: Seat[] = [];
  @Input() baseFare = 0;
  @Input() selectedSeatIds: number[] = [];
  @Input() loading = false;

  @Output() seatToggled = new EventEmitter<Seat>();

  readonly seatClassLabels: Record<SeatClass, string> = {
    FIRST: 'FIRST CLASS',
    BUSINESS: 'BUSINESS CLASS',
    ECONOMY: 'ECONOMY CLASS'
  };

  private readonly cabinOrder: SeatClass[] = ['FIRST', 'BUSINESS', 'ECONOMY'];
  private readonly blockSizes: Record<SeatClass, BlockSize> = {
    FIRST: { left: 1, middle: 1, right: 1 },
    BUSINESS: { left: 2, middle: 2, right: 2 },
    ECONOMY: { left: 3, middle: 3, right: 3 }
  };

  get classFareBands(): Array<{ seatClass: SeatClass; multiplier: number; amount: number }> {
    return this.cabinOrder
      .map((seatClass) => {
        const sample = this.seats.find((seat) => seat.seatClass === seatClass);
        if (!sample) {
          return null;
        }
        const multiplier = Number(sample.priceMultiplier ?? 1);
        return {
          seatClass,
          multiplier,
          amount: this.baseFare * multiplier
        };
      })
      .filter((item): item is { seatClass: SeatClass; multiplier: number; amount: number } => !!item);
  }

  get cabinSections(): CabinSection[] {
    return this.cabinOrder
      .map((seatClass) => {
        const rowsByNumber = new Map<number, Seat[]>();
        for (const seat of this.seats.filter((item) => item.seatClass === seatClass)) {
          const existing = rowsByNumber.get(seat.row) ?? [];
          existing.push(seat);
          rowsByNumber.set(seat.row, existing);
        }

        const rows = [...rowsByNumber.entries()]
          .sort((a, b) => a[0] - b[0])
          .map(([rowNumber, rowSeats]) => {
            const sortedSeats = [...rowSeats].sort((a, b) => a.column.localeCompare(b.column));
            const blocks = this.splitSeatsIntoBlocks(sortedSeats, seatClass);
            return {
              rowNumber,
              leftSeats: blocks.leftSeats,
              middleSeats: blocks.middleSeats,
              rightSeats: blocks.rightSeats,
              hasExtraLegroom: sortedSeats.some((seat) => seat.hasExtraLegroom)
            };
          });

        const firstRow = rows[0];
        return {
          seatClass,
          leftLabels: firstRow ? firstRow.leftSeats.map((seat) => seat.column) : [],
          middleLabels: firstRow ? firstRow.middleSeats.map((seat) => seat.column) : [],
          rightLabels: firstRow ? firstRow.rightSeats.map((seat) => seat.column) : [],
          rows
        };
      })
      .filter((section) => section.rows.length > 0);
  }

  isSelected(seat: Seat): boolean {
    return this.selectedSeatIds.includes(seat.seatId);
  }

  isUnavailable(seat: Seat): boolean {
    return seat.status === 'CONFIRMED' || seat.status === 'BLOCKED' || (seat.status === 'HELD' && !this.isSelected(seat));
  }

  onSeatClick(seat: Seat): void {
    if (this.isUnavailable(seat)) {
      return;
    }

    this.seatToggled.emit(seat);
  }

  trackSection(index: number, section: CabinSection): string {
    return `${section.seatClass}-${index}`;
  }

  trackRow(index: number, row: SeatRow): number {
    return row.rowNumber + index;
  }

  trackSeat(index: number, seat: Seat): number {
    return seat.seatId + index;
  }

  getSeatPrice(seat: Seat): number {
    return this.baseFare * Number(seat.priceMultiplier ?? 1);
  }

  private splitSeatsIntoBlocks(sortedSeats: Seat[], seatClass: SeatClass): Omit<SeatRow, 'rowNumber' | 'hasExtraLegroom'> {
    const blockSize = this.blockSizes[seatClass];
    const leftSeats = sortedSeats.slice(0, blockSize.left);
    const middleSeats = sortedSeats.slice(blockSize.left, blockSize.left + blockSize.middle);
    const rightSeats = sortedSeats.slice(blockSize.left + blockSize.middle, blockSize.left + blockSize.middle + blockSize.right);

    return { leftSeats, middleSeats, rightSeats };
  }
}
