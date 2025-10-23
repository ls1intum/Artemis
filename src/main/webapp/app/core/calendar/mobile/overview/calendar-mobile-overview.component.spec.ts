import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { CalendarMobileOverviewComponent } from './calendar-mobile-overview.component';
import { CalendarMobileMonthPresentationComponent } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { CalendarMobileDayPresentationComponent } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs, { Dayjs } from 'dayjs/esm';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

describe('CalendarMobileOverviewComponent', () => {
    let fixture: ComponentFixture<CalendarMobileOverviewComponent>;
    let component: CalendarMobileOverviewComponent;

    let dayToSelect: Dayjs;
    let firstDayOfCurrentMonth: Dayjs;
    let today: Dayjs;

    beforeEach(async () => {
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

        await TestBed.configureTestingModule({
            imports: [CalendarMobileOverviewComponent, CalendarMobileDayPresentationComponent],
            declarations: [FaIconComponent, MockComponent(CalendarMobileMonthPresentationComponent), MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: CalendarService,
                    useValue: calendarServiceMock,
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            paramMap: of({
                                get: () => '42',
                            }),
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                provideNoopAnimations(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileOverviewComponent);
        component = fixture.componentInstance;

        dayToSelect = dayjs('2025-05-15');
        firstDayOfCurrentMonth = dayjs('2025-05-01');
        today = dayjs();
        component.firstDateOfCurrentMonth.set(firstDayOfCurrentMonth);

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show CalendarMobileMonthPresentation when no day is selected', () => {
        component.selectedDate.set(undefined);
        fixture.detectChanges();

        const monthView = fixture.debugElement.query(By.directive(CalendarMobileMonthPresentationComponent));
        const dayView = fixture.debugElement.query(By.directive(CalendarMobileDayPresentationComponent));

        expect(monthView).toBeTruthy();
        expect(dayView).toBeFalsy();
    });

    it('should show CalendarMobileDayPresentation when a day is selected', () => {
        const someDay = dayjs('2025-05-15');
        component.selectedDate.set(someDay);
        fixture.detectChanges();

        const monthView = fixture.debugElement.query(By.directive(CalendarMobileMonthPresentationComponent));
        const dayView = fixture.debugElement.query(By.directive(CalendarMobileDayPresentationComponent));

        expect(dayView).toBeTruthy();
        expect(monthView).toBeFalsy();
    });

    it('goToPrevious should update month when no day selected', () => {
        component.selectedDate.set(undefined);
        fixture.detectChanges();

        const previousButton = fixture.debugElement.query(By.css('[data-testid="previous-button"]'));
        previousButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.firstDateOfCurrentMonth().isSame(firstDayOfCurrentMonth.subtract(1, 'month'), 'day')).toBeTrue();
    });

    it('goToPrevious should update selectedDay and month if new day falls into other month', () => {
        component.selectedDate.set(firstDayOfCurrentMonth);
        fixture.detectChanges();

        const previousButton = fixture.debugElement.query(By.css('[data-testid="previous-button"]'));
        previousButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedDate()?.isSame(firstDayOfCurrentMonth.subtract(1, 'day'), 'day')).toBeTrue();
        expect(component.firstDateOfCurrentMonth().isSame(firstDayOfCurrentMonth.subtract(1, 'month'), 'day')).toBeTrue();
    });

    it('goToPrevious should update selectedDay if new day falls into same month', () => {
        component.selectedDate.set(dayToSelect);
        fixture.detectChanges();

        const previousButton = fixture.debugElement.query(By.css('[data-testid="previous-button"]'));
        previousButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedDate()?.isSame(dayToSelect.subtract(1, 'day'), 'day')).toBeTrue();
        expect(component.firstDateOfCurrentMonth().isSame(firstDayOfCurrentMonth, 'day')).toBeTrue();
    });

    it('goToNext should update month when no day selected', () => {
        component.selectedDate.set(undefined);
        fixture.detectChanges();

        const nextButton = fixture.debugElement.query(By.css('[data-testid="next-button"]'));
        nextButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.firstDateOfCurrentMonth().isSame(firstDayOfCurrentMonth.add(1, 'month'), 'day')).toBeTrue();
    });

    it('goToNext should update selectedDay and month if new day falls into other month', () => {
        component.selectedDate.set(firstDayOfCurrentMonth.endOf('month'));
        fixture.detectChanges();

        const nextButton = fixture.debugElement.query(By.css('[data-testid="next-button"]'));
        nextButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedDate()?.isSame(firstDayOfCurrentMonth.add(1, 'month'), 'day')).toBeTrue();
        expect(component.firstDateOfCurrentMonth().isSame(firstDayOfCurrentMonth.add(1, 'month'), 'day')).toBeTrue();
    });

    it('goToNext should update selectedDay if new day falls into same month', () => {
        component.selectedDate.set(dayToSelect);
        fixture.detectChanges();

        const nextButton = fixture.debugElement.query(By.css('[data-testid="next-button"]'));
        nextButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedDate()?.isSame(dayToSelect.add(1, 'day'), 'day')).toBeTrue();
        expect(component.firstDateOfCurrentMonth().isSame(firstDayOfCurrentMonth, 'day')).toBeTrue();
    });

    it('goToToday should set selectedDay and month if a day was selected', () => {
        component.selectedDate.set(dayToSelect);
        fixture.detectChanges();

        const todayButton = fixture.debugElement.query(By.css('[data-testid="today-button"]'));
        todayButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedDate()?.isSame(today, 'day')).toBeTrue();
        expect(component.firstDateOfCurrentMonth().isSame(today.startOf('month'), 'day')).toBeTrue();
    });

    it('goToToday should only update month when no day selected', () => {
        component.selectedDate.set(undefined);
        fixture.detectChanges();

        const todayButton = fixture.debugElement.query(By.css('[data-testid="today-button"]'));
        todayButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.firstDateOfCurrentMonth().isSame(today.startOf('month'), 'day')).toBeTrue();
        expect(component.selectedDate()).toBeUndefined();
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
