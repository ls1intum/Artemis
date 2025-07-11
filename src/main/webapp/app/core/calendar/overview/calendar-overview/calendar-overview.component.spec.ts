import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarWeekPresentationComponent } from 'app/core/calendar/overview/calendar-week-presentation/calendar-week-presentation.component';
import { CalendarMonthPresentationComponent } from 'app/core/calendar/overview/calendar-month-presentation/calendar-month-presentation.component';
import { CalendarEventFilterComponent } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarOverviewComponent } from './calendar-overview.component';

describe('CalendarOverviewComponent', () => {
    let component: CalendarOverviewComponent;
    let fixture: ComponentFixture<CalendarOverviewComponent>;

    const calendarEventServiceMock = {
        eventMap: signal(new Map<string, CalendarEvent[]>()),
        loadEventsForCurrentMonth: jest.fn().mockReturnValue(of([])),
    };

    const activatedRouteMock = {
        parent: {
            paramMap: of(new Map([['courseId', '42']])),
        },
    };

    const translateServiceMock = {
        currentLang: 'en',
        onLangChange: of({ lang: 'en' }),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarOverviewComponent, CalendarWeekPresentationComponent, CalendarMonthPresentationComponent],
            declarations: [
                MockComponent(CalendarEventDetailPopoverComponent),
                MockComponent(CalendarDayBadgeComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CalendarEventFilterComponent),
            ],
            providers: [
                { provide: CalendarEventService, useValue: calendarEventServiceMock },
                { provide: ActivatedRoute, useValue: activatedRouteMock },
                { provide: TranslateService, useValue: translateServiceMock },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should update weeks and months correctly', () => {
        const initialFirstDayOfCurrentMonth = component.firstDayOfCurrentMonth();

        const previousButton = fixture.debugElement.query(By.css('#previous-button')).nativeElement;
        const nextButton = fixture.debugElement.query(By.css('#next-button')).nativeElement;
        const weekButton = fixture.debugElement.query(By.css('#week-button')).nativeElement;
        const monthButton = fixture.debugElement.query(By.css('#month-button')).nativeElement;
        expect(previousButton).toBeTruthy();
        expect(nextButton).toBeTruthy();
        expect(weekButton).toBeTruthy();
        expect(monthButton).toBeTruthy();

        expect(component.presentation()).toBe('month');
        expect(fixture.debugElement.query(By.css('calendar-desktop-month'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('calendar-desktop-week'))).toBeFalsy();

        previousButton.click();
        fixture.detectChanges();
        let firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        let firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expect(firstDayOfCurrentMonth.isSame(initialFirstDayOfCurrentMonth.subtract(1, 'month'), 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(firstDayOfCurrentMonth.startOf('isoWeek'), 'day')).toBeTrue();

        nextButton.click();
        fixture.detectChanges();
        firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expect(firstDayOfCurrentMonth.isSame(initialFirstDayOfCurrentMonth, 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(initialFirstDayOfCurrentMonth.startOf('isoWeek'), 'day')).toBeTrue();

        weekButton.click();
        fixture.detectChanges();
        expect(component.presentation()).toBe('week');
        expect(fixture.debugElement.query(By.css('calendar-desktop-week'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('calendar-desktop-month'))).toBeFalsy();

        nextButton.click();
        fixture.detectChanges();
        firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expect(firstDayOfCurrentMonth.isSame(initialFirstDayOfCurrentMonth, 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(initialFirstDayOfCurrentMonth.startOf('isoWeek').add(1, 'week'), 'day')).toBeTrue();

        previousButton.click();
        fixture.detectChanges();
        firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expect(firstDayOfCurrentMonth.isSame(initialFirstDayOfCurrentMonth.subtract(1, 'month'), 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(initialFirstDayOfCurrentMonth.startOf('isoWeek'), 'day')).toBeTrue();
    });

    it('should go to today', () => {
        expect(component).toBeTruthy();

        const todayButton = fixture.debugElement.query(By.css('#today-button')).nativeElement;
        const previousButton = fixture.debugElement.query(By.css('#previous-button')).nativeElement;

        expect(todayButton).toBeTruthy();
        expect(previousButton).toBeTruthy();

        previousButton.click();
        previousButton.click();
        fixture.detectChanges();

        todayButton.click();
        fixture.detectChanges();

        const today = dayjs();
        const expectedMonth = today.startOf('month');
        const expectedWeek = today.startOf('isoWeek');

        const actualMonth = component.firstDayOfCurrentMonth();
        const actualWeek = component.firstDayOfCurrentWeek();

        expect(actualMonth.isSame(expectedMonth, 'day')).toBeTrue();
        expect(actualWeek.isSame(expectedWeek, 'day')).toBeTrue();
    });

    it('should display correct month description', () => {
        const october = dayjs('2025-10-01');

        component.firstDayOfCurrentMonth.set(october.startOf('month'));
        component.firstDayOfCurrentWeek.set(october.startOf('isoWeek'));
        component.presentation.set('month');
        fixture.detectChanges();

        const heading = () => fixture.debugElement.query(By.css('.h3')).nativeElement.textContent.trim();

        expect(heading()).toBe('October 2025');

        const previousButton = fixture.debugElement.query(By.css('#previous-button')).nativeElement;
        previousButton.click();
        fixture.detectChanges();
        expect(heading()).toBe('September 2025');

        const nextButton = fixture.debugElement.query(By.css('#next-button')).nativeElement;
        nextButton.click();
        fixture.detectChanges();
        expect(heading()).toBe('October 2025');

        const weekButton = fixture.debugElement.query(By.css('#week-button')).nativeElement;
        weekButton.click();
        fixture.detectChanges();

        expect(heading()).toContain('September | October 2025');

        previousButton.click();
        fixture.detectChanges();
        expect(heading()).toBe('September 2025');

        nextButton.click();
        nextButton.click();
        fixture.detectChanges();
        expect(heading()).toBe('October 2025');
    });
});
