import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { getWeekdayNameKeys } from 'app/calendar/shared/util/calendar-util';
import { DAY_KEY_FORMAT, Holiday, HolidayDaySegment } from 'app/tutorialgroup/manage/holidays/holiday.model';

/** One cell of the grid. */
export interface HolidayCalendarDay {
    readonly date: dayjs.Dayjs;
    readonly dayKey: string;
    readonly dayOfMonth: number;
    readonly inDisplayedMonth: boolean;
    readonly isToday: boolean;
    readonly holidays: readonly HolidayDaySegment[];
    /** Sessions scheduled that day, or 0 when the day holds none. */
    readonly sessionCount: number;
}

export interface HolidayCalendarWeek {
    readonly id: string;
    readonly days: readonly HolidayCalendarDay[];
}

/**
 * The month grid of the holidays page.
 *
 * Deliberately not the calendar component the student course calendar uses: that one reads its events from an injected
 * `CalendarService` fed by the course calendar endpoint and keys colours and icons off `CalendarEventType`, so it cannot
 * render an arbitrary list of holidays without being rebuilt around an input. Keeping this grid separate leaves the
 * student calendar untouched.
 */
@Component({
    selector: 'jhi-holiday-month-grid',
    templateUrl: './holiday-month-grid.component.html',
    styleUrl: './holiday-month-grid.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TumUiButtonDirective, TranslateDirective, ArtemisTranslatePipe],
})
export class HolidayMonthGridComponent {
    /** Any day of the month to display; the grid derives the month from it. */
    readonly displayedMonth = input.required<dayjs.Dayjs>();
    readonly holidaysByDay = input.required<Map<string, HolidayDaySegment[]>>();
    readonly sessionCountsByDay = input.required<Map<string, number>>();
    /** Today in the course's time zone, so "today" is the course's day rather than the reader's. */
    readonly today = input.required<dayjs.Dayjs>();

    readonly monthChange = output<dayjs.Dayjs>();
    readonly holidaySelected = output<Holiday>();
    /** A click on an empty day, which opens the create dialog prefilled with that date. */
    readonly daySelected = output<dayjs.Dayjs>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    /** Reused from the course calendar so both grids label their columns identically. */
    protected readonly weekdayKeys = getWeekdayNameKeys();
    protected readonly monthLabel = computed(() => this.displayedMonth().locale(this.locale()).format('MMMM YYYY'));
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    protected readonly weeks = computed<HolidayCalendarWeek[]>(() => {
        const month = this.displayedMonth();
        const holidaysByDay = this.holidaysByDay();
        const sessionCountsByDay = this.sessionCountsByDay();
        const todayKey = this.today().format(DAY_KEY_FORMAT);

        const weeks: HolidayCalendarWeek[] = [];
        let date = month.startOf('month').startOf('isoWeek');
        const gridEnd = month.endOf('month').endOf('isoWeek');

        while (date.isBefore(gridEnd)) {
            const days: HolidayCalendarDay[] = [];
            for (let index = 0; index < 7; index++) {
                const dayKey = date.format(DAY_KEY_FORMAT);
                days.push({
                    date,
                    dayKey,
                    dayOfMonth: date.date(),
                    inDisplayedMonth: date.month() === month.month(),
                    isToday: dayKey === todayKey,
                    holidays: holidaysByDay.get(dayKey) ?? [],
                    sessionCount: sessionCountsByDay.get(dayKey) ?? 0,
                });
                date = date.add(1, 'day');
            }
            weeks.push({ id: days[0].dayKey, days });
        }
        return weeks;
    });

    protected showPreviousMonth(): void {
        this.monthChange.emit(this.displayedMonth().subtract(1, 'month').startOf('month'));
    }

    protected showNextMonth(): void {
        this.monthChange.emit(this.displayedMonth().add(1, 'month').startOf('month'));
    }

    protected showCurrentMonth(): void {
        this.monthChange.emit(this.today().startOf('month'));
    }

    /**
     * A day opens whichever holiday it already carries, and otherwise offers to create one.
     *
     * Days outside the displayed month are inert: clicking one would create a holiday in a month the reader is not
     * looking at, which reads as a misclick rather than an action.
     */
    protected onDayClick(day: HolidayCalendarDay): void {
        if (!day.inDisplayedMonth) {
            return;
        }
        if (day.holidays.length > 0) {
            this.holidaySelected.emit(day.holidays[0].holiday);
        } else {
            this.daySelected.emit(day.date);
        }
    }
}
