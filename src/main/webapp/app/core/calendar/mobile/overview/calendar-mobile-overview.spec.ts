import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarMobileOverviewComponent } from './calendar-mobile-overview';
import { CalendarMobileMonthPresentation } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { CalendarMobileDayPresentation } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation';
import { CalendarEventFilterComponent } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import dayjs, { Dayjs } from 'dayjs/esm';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CalendarMobileOverviewComponent', () => {
    let fixture: ComponentFixture<CalendarMobileOverviewComponent>;
    let component: CalendarMobileOverviewComponent;
    let calendarEventServiceMock: any;

    beforeEach(async () => {
        calendarEventServiceMock = {
            loadEventsForCurrentMonth: jest.fn().mockReturnValue(of(null)),
        };

        await TestBed.configureTestingModule({
            imports: [CalendarMobileOverviewComponent],
            declarations: [
                FaIconComponent,
                MockComponent(CalendarMobileMonthPresentation),
                MockComponent(CalendarMobileDayPresentation),
                MockComponent(CalendarEventFilterComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                {
                    provide: CalendarEventService,
                    useValue: calendarEventServiceMock,
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
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMobileOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show CalendarMobileMonthPresentation when no day is selected', () => {
        component.selectedDay.set(undefined);
        fixture.detectChanges();

        const monthView = fixture.debugElement.query(By.directive(CalendarMobileMonthPresentation));
        const dayView = fixture.debugElement.query(By.directive(CalendarMobileDayPresentation));

        expect(monthView).toBeTruthy();
        expect(dayView).toBeFalsy();
    });

    it('should show CalendarMobileDayPresentation when a day is selected', () => {
        const someDay = dayjs('2025-05-15');
        component.selectedDay.set(someDay);
        fixture.detectChanges();

        const monthView = fixture.debugElement.query(By.directive(CalendarMobileMonthPresentation));
        const dayView = fixture.debugElement.query(By.directive(CalendarMobileDayPresentation));

        expect(dayView).toBeTruthy();
        expect(monthView).toBeFalsy();
    });

    describe('navigation buttons', () => {
        let dayToSelect: Dayjs;
        let firstDayOfCurrentMonth: Dayjs;
        let today: Dayjs;

        beforeEach(() => {
            dayToSelect = dayjs('2025-05-15');
            firstDayOfCurrentMonth = dayjs('2025-05-01');
            today = dayjs();
            component.firstDayOfCurrentMonth.set(firstDayOfCurrentMonth);
        });

        it('goToPrevious should update month when no day selected', () => {
            component.selectedDay.set(undefined);
            fixture.detectChanges();

            const previousButton = fixture.debugElement.query(By.css('.chevron-button:first-child'));
            previousButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.firstDayOfCurrentMonth().isSame(firstDayOfCurrentMonth.subtract(1, 'month'), 'day')).toBeTrue();
        });

        it('goToPrevious should update selectedDay and month if new day falls into other month', () => {
            component.selectedDay.set(firstDayOfCurrentMonth);
            fixture.detectChanges();

            const previousButton = fixture.debugElement.query(By.css('.chevron-button:first-child'));
            previousButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.selectedDay()?.isSame(firstDayOfCurrentMonth.subtract(1, 'day'), 'day')).toBeTrue();
            expect(component.firstDayOfCurrentMonth().isSame(firstDayOfCurrentMonth.subtract(1, 'month'), 'day')).toBeTrue();
        });

        it('goToPrevious should update selectedDay if new day falls into same month', () => {
            component.selectedDay.set(dayToSelect);
            fixture.detectChanges();

            const previousButton = fixture.debugElement.query(By.css('.chevron-button:first-child'));
            previousButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.selectedDay()?.isSame(dayToSelect.subtract(1, 'day'), 'day')).toBeTrue();
            expect(component.firstDayOfCurrentMonth().isSame(firstDayOfCurrentMonth, 'day')).toBeTrue();
        });

        it('goToNext should update month when no day selected', () => {
            component.selectedDay.set(undefined);
            fixture.detectChanges();

            const nextButton = fixture.debugElement.query(By.css('.chevron-button:last-child'));
            nextButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.firstDayOfCurrentMonth().isSame(firstDayOfCurrentMonth.add(1, 'month'), 'day')).toBeTrue();
        });

        it('goToNext should update selectedDay and month if new day falls into other month', () => {
            component.selectedDay.set(firstDayOfCurrentMonth.endOf('month'));
            fixture.detectChanges();

            const nextButton = fixture.debugElement.query(By.css('.chevron-button:last-child'));
            nextButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.selectedDay()?.isSame(firstDayOfCurrentMonth.add(1, 'month'), 'day')).toBeTrue();
            expect(component.firstDayOfCurrentMonth().isSame(firstDayOfCurrentMonth.add(1, 'month'), 'day')).toBeTrue();
        });

        it('goToNext should update selectedDay if new day falls into same month', () => {
            component.selectedDay.set(dayToSelect);
            fixture.detectChanges();

            const nextButton = fixture.debugElement.query(By.css('.chevron-button:last-child'));
            nextButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.selectedDay()?.isSame(dayToSelect.add(1, 'day'), 'day')).toBeTrue();
            expect(component.firstDayOfCurrentMonth().isSame(firstDayOfCurrentMonth, 'day')).toBeTrue();
        });

        it('goToToday should set selectedDay and month if a day was selected', () => {
            component.selectedDay.set(dayToSelect);
            fixture.detectChanges();

            const todayButton = fixture.debugElement.query(By.css('.today-button'));
            todayButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.selectedDay()?.isSame(today, 'day')).toBeTrue();
            expect(component.firstDayOfCurrentMonth().isSame(today.startOf('month'), 'day')).toBeTrue();
        });

        it('goToToday should only update month when no day selected', () => {
            component.selectedDay.set(undefined);
            fixture.detectChanges();

            const todayButton = fixture.debugElement.query(By.css('.today-button'));
            todayButton.nativeElement.click();
            fixture.detectChanges();

            expect(component.firstDayOfCurrentMonth().isSame(today.startOf('month'), 'day')).toBeTrue();
            expect(component.selectedDay()).toBeUndefined();
        });
    });
});
