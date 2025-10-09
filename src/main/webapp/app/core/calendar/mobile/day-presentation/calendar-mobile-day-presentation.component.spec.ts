import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarMobileDayPresentationComponent } from './calendar-mobile-day-presentation.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';
import { MockComponent, ngMocks } from 'ng-mocks';
import dayjs from 'dayjs/esm';

describe('CalendarMobileDayPresentation', () => {
    let component: CalendarMobileDayPresentationComponent;
    let fixture: ComponentFixture<CalendarMobileDayPresentationComponent>;

    const selectedDay = dayjs('2025-08-06');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileDayPresentationComponent, CalendarDayBadgeComponent, MockComponent(CalendarEventsPerDaySectionComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileDayPresentationComponent);

        fixture.componentRef.setInput('selectedDate', selectedDay);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render 7 day badges for the selected week', () => {
        const dayBadgeComponents = ngMocks.findInstances(CalendarDayBadgeComponent);
        expect(dayBadgeComponents).toHaveLength(7);
    });

    it('should mark only the selected day as selected', () => {
        const dayBadgeComponents = ngMocks.findInstances(CalendarDayBadgeComponent);

        const selectedDayBadge = dayBadgeComponents.find((dayBadgeComponent) => dayBadgeComponent.day().isSame(selectedDay, 'day'));

        expect(selectedDayBadge?.isSelectedDay()).toBeTrue();

        const nonSelectedDayBadges = dayBadgeComponents.filter((dayBadgeComponent) => !dayBadgeComponent.day().isSame(selectedDay, 'day'));

        for (const nonSelectedDayBadge of nonSelectedDayBadges) {
            expect(nonSelectedDayBadge.isSelectedDay()).toBeFalse();
        }
    });
});
