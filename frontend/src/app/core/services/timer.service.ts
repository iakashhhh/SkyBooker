import { Injectable } from '@angular/core';
import { BehaviorSubject, interval, Subscription } from 'rxjs';

/**
 * Manages a per-session countdown timer for held seats.
 */
@Injectable({
  providedIn: 'root'
})
export class TimerService {
  private readonly countdownSecondsSubject = new BehaviorSubject<number>(0);
  readonly countdownSeconds$ = this.countdownSecondsSubject.asObservable();

  private timerSubscription?: Subscription;

  start(seconds: number): void {
    if (this.timerSubscription) {
      return;
    }

    this.countdownSecondsSubject.next(seconds);
    this.timerSubscription = interval(1000).subscribe(() => {
      const nextValue = this.countdownSecondsSubject.getValue() - 1;
      this.countdownSecondsSubject.next(nextValue);
      if (nextValue <= 0) {
        this.stop();
      }
    });
  }

  reset(seconds = 0): void {
    this.stop();
    this.countdownSecondsSubject.next(seconds);
  }

  private stop(): void {
    this.timerSubscription?.unsubscribe();
    this.timerSubscription = undefined;
  }
}
