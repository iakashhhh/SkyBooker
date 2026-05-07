import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, OnDestroy, QueryList, ViewChildren } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

type SearchField = 'originText' | 'destinationText';

interface Destination {
  city: string;
  country: string;
  price: string;
  imageUrl: string;
}

interface AirportOption {
  code: string;
  city: string;
  airport: string;
}

interface FeatureItem {
  icon: string;
  title: string;
  description: string;
}

interface Testimonial {
  name: string;
  role: string;
  quote: string;
  avatarUrl: string;
}

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.css'
})
export class LandingPageComponent implements AfterViewInit, OnDestroy {
  @ViewChildren('revealCard') revealCards!: QueryList<ElementRef<HTMLElement>>;
  @ViewChildren('popIcon') popIcons!: QueryList<ElementRef<HTMLElement>>;
  readonly todayDate = this.toIsoDate(new Date());
  readonly currentMonth = this.todayDate.slice(0, 7);
  readonly defaultReturnDate = this.shiftIsoDate(this.todayDate, 5);

  readonly airportOptions: AirportOption[] = [
    { code: 'DEL', city: 'Delhi', airport: 'Indira Gandhi International Airport' },
    { code: 'MUM', city: 'Mumbai', airport: 'Chhatrapati Shivaji Maharaj International Airport' },
    { code: 'BLR', city: 'Bengaluru', airport: 'Kempegowda International Airport' },
    { code: 'GOI', city: 'Goa', airport: 'Goa International Airport' },
    { code: 'HYD', city: 'Hyderabad', airport: 'Rajiv Gandhi International Airport' },
    { code: 'MAA', city: 'Chennai', airport: 'Chennai International Airport' },
    { code: 'CCU', city: 'Kolkata', airport: 'Netaji Subhas Chandra Bose International Airport' },
    { code: 'JAI', city: 'Jaipur', airport: 'Jaipur International Airport' },
    { code: 'PNQ', city: 'Pune', airport: 'Pune International Airport' }
  ];

  readonly cityToCode: Record<string, string> = {
    delhi: 'DEL',
    mumbai: 'MUM',
    bangalore: 'BLR',
    bengaluru: 'BLR',
    goa: 'GOI',
    hyderabad: 'HYD',
    chennai: 'MAA',
    kolkata: 'CCU',
    jaipur: 'JAI',
    pune: 'PNQ'
  };
  readonly citySuggestions: Record<SearchField, AirportOption[]> = {
    originText: [],
    destinationText: []
  };
  activeCityField: SearchField | null = null;
  private blurTimeoutId?: ReturnType<typeof setTimeout>;

  readonly form = this.formBuilder.group({
    tripType: ['ROUND_TRIP', Validators.required],
    dateMode: ['EXACT', Validators.required],
    originText: ['', Validators.required],
    destinationText: ['', Validators.required],
    journeyDate: [this.todayDate, Validators.required],
    returnDate: [this.defaultReturnDate],
    journeyMonth: [this.currentMonth],
    returnMonth: [this.currentMonth],
    seatClass: ['ECONOMY', Validators.required]
  });

  submitError = '';
  readonly destinations: Destination[] = [
    {
      city: 'Agra',
      country: 'India',
      price: '₹4,999',
      imageUrl: 'https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=1200&q=80'
    },
    {
      city: 'Mumbai',
      country: 'India',
      price: '₹5,499',
      imageUrl: 'https://images.unsplash.com/photo-1595658658481-d53d3f999875?auto=format&fit=crop&w=1200&q=80'
    },
    {
      city: 'Bangalore',
      country: 'India',
      price: '₹6,250',
      imageUrl: 'https://images.unsplash.com/photo-1596176530529-78163a4f7af2?auto=format&fit=crop&w=1200&q=80'
    },
    {
      city: 'Goa',
      country: 'India',
      price: '₹7,999',
      imageUrl: 'https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=1200&q=80'
    }
  ];

  readonly featureItems: FeatureItem[] = [
    {
      icon: '⌕',
      title: 'Compare Airlines',
      description: 'Search and compare prices across hundreds of airlines.'
    },
    {
      icon: '$',
      title: 'Best Price Guarantee',
      description: 'Get transparent fares with price confidence for every route.'
    },
    {
      icon: '◔',
      title: 'Real-time Updates',
      description: 'Track flight changes instantly, from delays to gate updates.'
    },
    {
      icon: '✈',
      title: 'Easy Booking',
      description: 'Book your next journey in a few smooth and secure steps.'
    }
  ];

  readonly testimonials: Testimonial[] = [
    {
      name: 'Sarah Johnson',
      role: 'Frequent Traveler',
      quote: 'Amazing service! Found the cheapest flights to Europe and the booking process was incredibly smooth.',
      avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80'
    },
    {
      name: 'Michael Chen',
      role: 'Business Traveler',
      quote: 'The best flight comparison tool I have used. Saved over ₹25,000 on my family vacation.',
      avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80'
    },
    {
      name: 'Emma Williams',
      role: 'Travel Blogger',
      quote: 'Simple, fast, and reliable. I use SkyBooker for all my business travel and trip planning.',
      avatarUrl: 'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=200&q=80'
    }
  ];

  readonly trustStats = [
    { icon: '✈', label: '1000+ Airlines' },
    { icon: '⛨', label: 'Best Price Guarantee' },
    { icon: '◷', label: '24/7 Support' },
    { icon: '☻', label: 'Trusted by Millions' }
  ];

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly router: Router
  ) {}

  ngAfterViewInit(): void {
    this.observeRevealElements();
  }

  ngOnDestroy(): void {
    if (this.blurTimeoutId) {
      window.clearTimeout(this.blurTimeoutId);
    }
  }

  onCityFocus(field: SearchField): void {
    this.activeCityField = field;
    const control = this.form.controls[field];
    this.citySuggestions[field] = this.filterAirportOptions(String(control.value ?? ''));
  }

  onCityBlur(field: SearchField): void {
    window.clearTimeout(this.blurTimeoutId);
    this.blurTimeoutId = window.setTimeout(() => {
      if (this.activeCityField === field) {
        this.activeCityField = null;
      }
    }, 120);
  }

  onCityInput(field: SearchField, rawValue: string): void {
    const normalized = rawValue.trimStart();
    this.form.controls[field].setValue(normalized, { emitEvent: false });
    this.activeCityField = field;
    this.citySuggestions[field] = this.filterAirportOptions(normalized);
  }

  selectCity(field: SearchField, option: AirportOption): void {
    this.form.controls[field].setValue(`${option.city} (${option.code})`);
    this.citySuggestions[field] = [];
    this.activeCityField = null;
  }

  showCityDropdown(field: SearchField): boolean {
    return this.activeCityField === field && this.citySuggestions[field].length > 0;
  }

  submitSearch(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const originCode = this.resolveAirportCode(value.originText ?? '');
    const destinationCode = this.resolveAirportCode(value.destinationText ?? '');

    if (!originCode || !destinationCode) {
      this.submitError = 'Please enter supported city names or airport codes for From and To.';
      return;
    }

    if (originCode === destinationCode) {
      this.submitError = 'Origin and destination cannot be the same.';
      return;
    }

    const tripType = value.tripType ?? 'ROUND_TRIP';
    const dateMode = value.dateMode ?? 'EXACT';

    if (dateMode === 'EXACT') {
      if (!value.journeyDate) {
        this.submitError = 'Please select a departure date.';
        return;
      }

      if (value.journeyDate < this.todayDate) {
        this.submitError = 'Past departure dates are not allowed.';
        return;
      }

      if (tripType === 'ROUND_TRIP') {
        if (!value.returnDate) {
          this.submitError = 'Please select a return date.';
          return;
        }

        if (value.returnDate <= value.journeyDate) {
          this.submitError = 'Return date must be after departure date.';
          return;
        }
      }
    }

    if (dateMode === 'FLEXIBLE') {
      if (!value.journeyMonth) {
        this.submitError = 'Please select a departure month.';
        return;
      }

      if (value.journeyMonth < this.currentMonth) {
        this.submitError = 'Flexible searches must use the current month or a future month.';
        return;
      }

      if (tripType === 'ROUND_TRIP') {
        if (!value.returnMonth) {
          this.submitError = 'Please select a return month.';
          return;
        }

        if (value.returnMonth < value.journeyMonth) {
          this.submitError = 'Return month must be same or after departure month.';
          return;
        }
      }
    }

    this.submitError = '';

    const journeyDate = dateMode === 'FLEXIBLE'
      ? `${value.journeyMonth ?? '2026-04'}-01`
      : (value.journeyDate ?? '');
    const returnDate = tripType === 'ROUND_TRIP'
      ? (dateMode === 'FLEXIBLE' ? `${value.returnMonth ?? value.journeyMonth ?? '2026-04'}-02` : (value.returnDate ?? ''))
      : '';

    this.router.navigate(['/flights/results'], {
      queryParams: {
        tripType,
        origin: originCode,
        destination: destinationCode,
        journeyDate,
        returnDate,
        flexibleDate: dateMode === 'FLEXIBLE',
        journeyMonth: value.journeyMonth ?? '',
        returnMonth: value.returnMonth ?? '',
        seatClass: value.seatClass ?? 'ECONOMY',
        sortBy: 'price_asc'
      }
    });
  }

  private resolveAirportCode(input: string): string | null {
    const trimmed = input.trim();
    if (!trimmed) {
      return null;
    }

    const formatted = trimmed.match(/\(([A-Za-z]{3})\)$/);
    if (formatted) {
      return formatted[1].toUpperCase();
    }

    if (/^[A-Za-z]{3}$/.test(trimmed)) {
      return trimmed.toUpperCase();
    }

    return this.cityToCode[trimmed.toLowerCase()] ?? null;
  }

  private filterAirportOptions(rawQuery: string): AirportOption[] {
    const query = rawQuery.trim().toLowerCase();
    if (!query) {
      return this.airportOptions.slice(0, 6);
    }

    return this.airportOptions
      .filter((option) =>
        option.code.toLowerCase().includes(query)
        || option.city.toLowerCase().includes(query)
        || option.airport.toLowerCase().includes(query)
      )
      .slice(0, 6);
  }

  private observeRevealElements(): void {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.2 }
    );

    this.revealCards.forEach((el) => observer.observe(el.nativeElement));
    this.popIcons.forEach((el) => observer.observe(el.nativeElement));
  }

  private toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private shiftIsoDate(isoDate: string, offsetDays: number): string {
    const date = new Date(`${isoDate}T00:00:00`);
    date.setDate(date.getDate() + offsetDays);
    return this.toIsoDate(date);
  }
}
