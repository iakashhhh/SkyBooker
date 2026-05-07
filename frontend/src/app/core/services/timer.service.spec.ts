import { fakeAsync, TestBed, tick } from '@angular/core/testing';

import { TimerService } from './timer.service';

describe('TimerService', () => {
  let service: TimerService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TimerService]
    });

    service = TestBed.inject(TimerService);
  });

  it('counts down to zero and then stops', fakeAsync(() => {
    const values: number[] = [];
    service.countdownSeconds$.subscribe((value) => values.push(value));

    service.start(2);
    tick(1000);
    expect(values[values.length - 1]).toBe(1);

    tick(1000);
    expect(values[values.length - 1]).toBe(0);

    tick(2000);
    expect(values[values.length - 1]).toBe(0);
  }));

  it('ignores repeated start while countdown is running', fakeAsync(() => {
    const values: number[] = [];
    service.countdownSeconds$.subscribe((value) => values.push(value));

    service.start(3);
    tick(1000);
    expect(values[values.length - 1]).toBe(2);

    service.start(10);
    tick(1000);
    expect(values[values.length - 1]).toBe(1);

    service.reset(0);
    tick(0);
  }));

  it('reset clears active timer and sets explicit value', fakeAsync(() => {
    const values: number[] = [];
    service.countdownSeconds$.subscribe((value) => values.push(value));

    service.start(5);
    tick(1000);
    service.reset(9);

    expect(values[values.length - 1]).toBe(9);

    tick(2000);
    expect(values[values.length - 1]).toBe(9);
  }));
});
