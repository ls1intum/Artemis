import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarMobileDayPresentation } from './calendar-mobile-day-presentation';

describe('CalendarMobileDayPresentation', () => {
    let component: CalendarMobileDayPresentation;
    let fixture: ComponentFixture<CalendarMobileDayPresentation>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileDayPresentation],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileDayPresentation);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
