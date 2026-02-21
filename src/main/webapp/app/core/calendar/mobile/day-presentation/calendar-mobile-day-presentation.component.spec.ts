import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarMobileDayPresentationComponent } from './calendar-mobile-day-presentation.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';
import { MockComponent } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';

describe('CalendarMobileDayPresentationComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CalendarMobileDayPresentationComponent;
    let fixture: ComponentFixture<CalendarMobileDayPresentationComponent>;

    const selectedDay = dayjs('2025-08-06');

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMobileDayPresentationComponent, CalendarDayBadgeComponent, MockComponent(CalendarEventsPerDaySectionComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileDayPresentationComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('date', selectedDay);
        TestBed.tick();
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render 7 day badges for the selected week', () => {
        const dayBadgeElements = fixture.debugElement.queryAll(By.directive(CalendarDayBadgeComponent));
        expect(dayBadgeElements).toHaveLength(7);
    });

    it('should mark only the selected day as selected', () => {
        const dayBadgeElements = fixture.debugElement.queryAll(By.directive(CalendarDayBadgeComponent));

        // Ensure components are rendered
        expect(dayBadgeElements).toHaveLength(7);

        const dayBadgeComponents = dayBadgeElements.map((de) => de.componentInstance as CalendarDayBadgeComponent);

        const selectedDayBadge = dayBadgeComponents.find((dayBadgeComponent) => dayBadgeComponent.date().isSame(selectedDay, 'day'));

        expect(selectedDayBadge).toBeDefined();
        expect(selectedDayBadge?.isSelectedDay()).toBe(true);

        const nonSelectedDayBadges = dayBadgeComponents.filter((dayBadgeComponent) => !dayBadgeComponent.date().isSame(selectedDay, 'day'));

        for (const nonSelectedDayBadge of nonSelectedDayBadges) {
            expect(nonSelectedDayBadge.isSelectedDay()).toBe(false);
        }
    });
});
