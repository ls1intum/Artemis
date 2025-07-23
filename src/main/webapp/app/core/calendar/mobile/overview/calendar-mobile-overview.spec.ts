import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarMobileOverview, CalendarMobileOverviewComponent } from './calendar-mobile-overview';

describe('CalendarMobileOverview', () => {
    let component: CalendarMobileOverviewComponent;
    let fixture: ComponentFixture<CalendarMobileOverviewComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileOverviewComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
