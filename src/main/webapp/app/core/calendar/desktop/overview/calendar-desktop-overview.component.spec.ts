import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarDesktopWeekPresentationComponent } from 'app/core/calendar/desktop/week-presentation/calendar-desktop-week-presentation.component';
import { CalendarDesktopMonthPresentationComponent } from 'app/core/calendar/desktop/month-presentation/calendar-desktop-month-presentation.component';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarDesktopOverviewComponent } from './calendar-desktop-overview.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

describe('CalendarDesktopOverviewComponent', () => {
    let component: CalendarDesktopOverviewComponent;
    let fixture: ComponentFixture<CalendarDesktopOverviewComponent>;

    const calendarServiceMock = {
        eventMap: signal(new Map<string, CalendarEvent[]>()),
        loadEventsForCurrentMonth: jest.fn().mockReturnValue(of([])),
        subscriptionToken: signal('testToken'),
        loadSubscriptionToken: jest.fn().mockReturnValue(of([])),
        includedEventFilterOptions: signal([
            CalendarEventFilterOption.LectureEvents,
            CalendarEventFilterOption.ExerciseEvents,
            CalendarEventFilterOption.TutorialEvents,
            CalendarEventFilterOption.ExamEvents,
        ]),
    };

    const activatedRouteMock = {
        parent: {
            paramMap: of(new Map([['courseId', '42']])),
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDesktopOverviewComponent, CalendarDesktopWeekPresentationComponent, CalendarDesktopMonthPresentationComponent, FaIconComponent],
            declarations: [MockComponent(CalendarDayBadgeComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: CalendarService, useValue: calendarServiceMock },
                { provide: ActivatedRoute, useValue: activatedRouteMock },
                { provide: TranslateService, useClass: MockTranslateService },
                provideNoopAnimations(),
            ],
        }).compileComponents();

        const translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        translateService.currentLang = 'en';

        fixture = TestBed.createComponent(CalendarDesktopOverviewComponent);
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
        const selectButton = fixture.debugElement.query(By.css('#presentation-select-button'));
        expect(previousButton).toBeTruthy();
        expect(nextButton).toBeTruthy();
        expect(selectButton).toBeTruthy();

        expect(component.selectedPresentation()).toBe('month');
        expect(fixture.debugElement.query(By.css('jhi-calendar-desktop-month-presentation'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('jhi-calendar-desktop-week-presentation'))).toBeFalsy();

        previousButton.click();
        fixture.detectChanges();
        let firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        let firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        let expectedFirstDayOfCurrentMonth = initialFirstDayOfCurrentMonth.subtract(1, 'month');
        let expectedFirstDayOfCurrentWeek = firstDayOfCurrentMonth.startOf('isoWeek');
        expect(firstDayOfCurrentMonth.isSame(expectedFirstDayOfCurrentMonth, 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(expectedFirstDayOfCurrentWeek, 'day')).toBeTrue();

        nextButton.click();
        fixture.detectChanges();
        firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expectedFirstDayOfCurrentMonth = initialFirstDayOfCurrentMonth;
        expectedFirstDayOfCurrentWeek = initialFirstDayOfCurrentMonth.startOf('isoWeek');
        expect(firstDayOfCurrentMonth.isSame(expectedFirstDayOfCurrentMonth, 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(expectedFirstDayOfCurrentWeek, 'day')).toBeTrue();

        component.selectedPresentation.set('week');
        fixture.detectChanges();
        expect(component.selectedPresentation()).toBe('week');
        expect(fixture.debugElement.query(By.css('jhi-calendar-desktop-week-presentation'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('jhi-calendar-desktop-month-presentation'))).toBeFalsy();

        nextButton.click();
        fixture.detectChanges();
        firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expectedFirstDayOfCurrentMonth = initialFirstDayOfCurrentMonth;
        expectedFirstDayOfCurrentWeek = initialFirstDayOfCurrentMonth.startOf('isoWeek').add(1, 'week');
        expect(firstDayOfCurrentMonth.isSame(expectedFirstDayOfCurrentMonth, 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(expectedFirstDayOfCurrentWeek, 'day')).toBeTrue();

        previousButton.click();
        fixture.detectChanges();
        firstDayOfCurrentMonth = component.firstDayOfCurrentMonth();
        firstDayOfCurrentWeek = component.firstDayOfCurrentWeek();
        expectedFirstDayOfCurrentMonth = initialFirstDayOfCurrentMonth.startOf('isoWeek').isBefore(initialFirstDayOfCurrentMonth)
            ? initialFirstDayOfCurrentMonth.subtract(1, 'month')
            : initialFirstDayOfCurrentMonth;
        expectedFirstDayOfCurrentWeek = initialFirstDayOfCurrentMonth.startOf('isoWeek');
        expect(firstDayOfCurrentMonth.isSame(expectedFirstDayOfCurrentMonth, 'day')).toBeTrue();
        expect(firstDayOfCurrentWeek.isSame(expectedFirstDayOfCurrentWeek, 'day')).toBeTrue();
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
        component.selectedPresentation.set('month');
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

        component.selectedPresentation.set('week');
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

    it('should open subscription popover when the subscribe button is clicked', () => {
        const popoverDebugElement = fixture.debugElement.query(By.directive(CalendarSubscriptionPopoverComponent));
        expect(popoverDebugElement).toBeTruthy();

        const popover = popoverDebugElement.componentInstance as CalendarSubscriptionPopoverComponent;
        const openSpy = jest.spyOn(popover, 'open');

        const subscribeButton = fixture.debugElement.query(By.css('[data-testid="subscribe-button"]')).nativeElement;
        expect(subscribeButton).toBeTruthy();

        subscribeButton.click();
        fixture.detectChanges();

        expect(openSpy).toHaveBeenCalledOnce();
    });
});
