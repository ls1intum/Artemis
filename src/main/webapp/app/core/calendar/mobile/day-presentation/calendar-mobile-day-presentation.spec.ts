import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarMobileDayPresentation } from './calendar-mobile-day-presentation';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';
import { MockComponent, ngMocks } from 'ng-mocks';
import dayjs from 'dayjs/esm';

describe('CalendarMobileDayPresentation', () => {
    let component: CalendarMobileDayPresentation;
    let fixture: ComponentFixture<CalendarMobileDayPresentation>;

    const selectedDay = dayjs('2025-08-06');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileDayPresentation, CalendarDayBadgeComponent, MockComponent(CalendarEventsPerDaySectionComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileDayPresentation);

        fixture.componentRef.setInput('selectedDay', selectedDay);
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

    it('should apply no-scroll class when isEventSelected is true', () => {
        component.isEventSelected.set(true);
        fixture.detectChanges();

        const scrollContainerElement = fixture.nativeElement.querySelector('.scroll-container');
        expect(scrollContainerElement.classList).toContain('no-scroll');
    });

    it('should update isEventSelected signal when child emits true', () => {
        const eventsSectionComponent = ngMocks.findInstance(CalendarEventsPerDaySectionComponent);
        eventsSectionComponent.isEventSelected.emit(true);
        fixture.detectChanges();

        expect(component.isEventSelected()).toBeTrue();
    });
});
