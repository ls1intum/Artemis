import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { CalendarDesktopWeekPresentationComponent } from './calendar-desktop-week-presentation.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CalendarDesktopWeekPresentationComponent', () => {
    let fixture: ComponentFixture<CalendarDesktopWeekPresentationComponent>;

    const startOfMonday = dayjs('2025-05-05');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDesktopWeekPresentationComponent],
            declarations: [
                MockComponent(CalendarDayBadgeComponent),
                MockComponent(CalendarEventsPerDaySectionComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDesktopWeekPresentationComponent);

        fixture.componentRef.setInput('firstDayOfCurrentWeek', startOfMonday);
        fixture.detectChanges();
    });

    it('should render 7 day-info divs for each weekday', () => {
        const dayInfoElements = fixture.debugElement.queryAll(By.css('.day-info'));
        expect(dayInfoElements).toHaveLength(7);
    });
});
