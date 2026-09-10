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
import { DAY_KEY_FORMAT, Holiday, endsAtEndOfDay, startsAtBeginningOfDay } from 'app/tutorialgroup/manage/holidays/holiday.model';

/** Days in a week, and so the number of columns a week is laid out in. */
const DAYS_PER_WEEK = 7;

/** Height of one row of bars, in rem. Two lines, so a holiday can name itself and its times without crowding. */
const LANE_HEIGHT_REM = 2.75;

/** Room the day numbers take above the bars, in rem. */
const HEADER_HEIGHT_REM = 2;

/** A week with no holiday still gets this much room, so an empty month keeps its shape. */
const MIN_WEEK_HEIGHT_REM = 5.5;

/** Breathing room below the last lane, so a full week does not end flush against its border. */
const WEEK_BOTTOM_PADDING_REM = 0.5;

/**
 * One holiday as a single bar across the days of one week.
 *
 * A holiday running Friday to Monday becomes two bars, one per week row, because a row cannot be spanned across a line
 * break - but within a row it is one element rather than one per day. That is what lets the reason be written once at
 * the full width of the run, instead of being truncated into every cell it passes through.
 */
export interface HolidayBar {
    readonly key: string;
    readonly holiday: Holiday;
    readonly reason: string;
    /** Column the bar starts in, 0 for Monday. */
    readonly startColumn: number;
    /** How many columns it covers in this week. */
    readonly span: number;
    /** Which row of bars it sits in, so two holidays sharing a day do not collide. */
    readonly lane: number;
    /** True when the holiday itself begins here rather than continuing from the previous week. */
    readonly startsRun: boolean;
    readonly endsRun: boolean;
    /** Set only when the holiday starts partway through its first day, and that day is in this week. */
    readonly startTime?: string;
    /** Set only when it ends partway through its last day. */
    readonly endTime?: string;
}

/** One cell of the grid. Carries the day number and its session count; holidays are drawn over it as bars. */
export interface HolidayCalendarDay {
    readonly date: dayjs.Dayjs;
    readonly dayKey: string;
    readonly dayOfMonth: number;
    readonly inDisplayedMonth: boolean;
    readonly isToday: boolean;
    readonly hasHoliday: boolean;
    /** Sessions scheduled that day, or 0 when the day holds none. */
    readonly sessionCount: number;
    /**
     * Whether this cell draws the line to its right, and the one below it.
     *
     * Only the lines between cells are drawn: the card around the calendar already draws its own edge, and a cell
     * drawing one there too laid a second grey line beside it.
     */
    readonly drawsRightBorder: boolean;
    readonly drawsBottomBorder: boolean;
}

export interface HolidayCalendarWeek {
    readonly id: string;
    readonly days: readonly HolidayCalendarDay[];
    readonly bars: readonly HolidayBar[];
    /** Rows of bars this week needs, which makes its cells as tall as they have to be and no taller. */
    readonly laneCount: number;
    /** Height of the week in rem, derived from its lanes so a busy week grows and an empty one does not. */
    readonly heightRem: number;
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
    readonly holidays = input.required<readonly Holiday[]>();
    readonly sessionCountsByDay = input.required<Map<string, number>>();
    /** Today in the course's time zone, so "today" is the course's day rather than the reader's. */
    readonly today = input.required<dayjs.Dayjs>();

    readonly monthChange = output<dayjs.Dayjs>();
    readonly holidaySelected = output<Holiday>();
    /** A click on a day, which opens the create dialog prefilled with that date. */
    readonly daySelected = output<dayjs.Dayjs>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    /** Reused from the course calendar so both grids label their columns identically. */
    protected readonly weekdayKeys = getWeekdayNameKeys();
    protected readonly monthLabel = computed(() => this.displayedMonth().locale(this.locale()).format('MMMM YYYY'));
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    protected readonly headerHeightRem = HEADER_HEIGHT_REM;
    protected readonly laneHeightRem = LANE_HEIGHT_REM;
    protected readonly columnWidthPercent = 100 / DAYS_PER_WEEK;

    protected readonly weeks = computed<HolidayCalendarWeek[]>(() => {
        const month = this.displayedMonth();
        const holidays = this.holidays();
        const sessionCountsByDay = this.sessionCountsByDay();
        const todayKey = this.today().format(DAY_KEY_FORMAT);

        const weeks: HolidayCalendarWeek[] = [];
        let weekStart = month.startOf('month').startOf('isoWeek');
        const gridEnd = month.endOf('month').endOf('isoWeek');

        while (weekStart.isBefore(gridEnd)) {
            const bars = this.barsForWeek(holidays, weekStart);
            const coveredColumns = new Set(bars.flatMap((bar) => Array.from({ length: bar.span }, (_, offset) => bar.startColumn + offset)));

            const isLastWeek = !weekStart.add(1, 'week').isBefore(gridEnd);
            const days: HolidayCalendarDay[] = [];
            for (let column = 0; column < DAYS_PER_WEEK; column++) {
                const date = weekStart.add(column, 'day');
                const dayKey = date.format(DAY_KEY_FORMAT);
                days.push({
                    drawsRightBorder: column < DAYS_PER_WEEK - 1,
                    drawsBottomBorder: !isLastWeek,
                    date,
                    dayKey,
                    dayOfMonth: date.date(),
                    inDisplayedMonth: date.month() === month.month(),
                    isToday: dayKey === todayKey,
                    hasHoliday: coveredColumns.has(column),
                    sessionCount: sessionCountsByDay.get(dayKey) ?? 0,
                });
            }

            const laneCount = bars.reduce((highest, bar) => Math.max(highest, bar.lane + 1), 0);
            weeks.push({
                id: days[0].dayKey,
                days,
                bars,
                laneCount,
                heightRem: Math.max(MIN_WEEK_HEIGHT_REM, HEADER_HEIGHT_REM + laneCount * LANE_HEIGHT_REM + WEEK_BOTTOM_PADDING_REM),
            });
            weekStart = weekStart.add(1, 'week');
        }
        return weeks;
    });

    /**
     * Clips every holiday reaching into this week down to the columns it covers here.
     *
     * A holiday's own times describe its two ends, so a time is carried only where that end actually falls: the middle
     * of a run says nothing beyond the reason, because those days are cancelled outright.
     */
    private barsForWeek(holidays: readonly Holiday[], weekStart: dayjs.Dayjs): HolidayBar[] {
        const weekEnd = weekStart.add(DAYS_PER_WEEK - 1, 'day');
        const bars: HolidayBar[] = [];

        for (const holiday of holidays) {
            const firstDay = holiday.start.startOf('day');
            const lastDay = holiday.lastDay;
            if (lastDay.isBefore(weekStart, 'day') || firstDay.isAfter(weekEnd, 'day')) {
                continue;
            }

            const clipStart = firstDay.isBefore(weekStart, 'day') ? weekStart : firstDay;
            const clipEnd = lastDay.isAfter(weekEnd, 'day') ? weekEnd : lastDay;
            const startsRun = clipStart.isSame(firstDay, 'day');
            const endsRun = clipEnd.isSame(lastDay, 'day');
            const startsPartway = startsRun && !startsAtBeginningOfDay(holiday.start);
            const endsPartway = endsRun && !endsAtEndOfDay(holiday.end);
            const startColumn = clipStart.diff(weekStart, 'day');
            const span = clipEnd.diff(clipStart, 'day') + 1;

            bars.push({
                key: `${holiday.period.id}-${clipStart.format(DAY_KEY_FORMAT)}`,
                holiday,
                reason: holiday.reason,
                startColumn,
                span,
                lane: this.firstFreeLane(bars, startColumn, span),
                startsRun,
                endsRun,
                startTime: startsPartway ? holiday.startTime : undefined,
                endTime: endsPartway ? holiday.endTime : undefined,
            });
        }
        return bars;
    }

    /** The lowest row no bar already over these columns is using, so two holidays on a day stack rather than collide. */
    private firstFreeLane(placed: readonly HolidayBar[], startColumn: number, span: number): number {
        const overlapping = placed.filter((bar) => bar.startColumn < startColumn + span && bar.startColumn + bar.span > startColumn);
        let lane = 0;
        while (overlapping.some((bar) => bar.lane === lane)) {
            lane++;
        }
        return lane;
    }

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
     * A day offers to create a holiday on it whether or not it already carries one: the bars open an existing holiday,
     * so a second holiday on the same day stays reachable.
     *
     * Days outside the displayed month are inert, because creating a holiday in a month the reader is not looking at
     * reads as a misclick rather than an action.
     */
    protected onDayClick(day: HolidayCalendarDay): void {
        if (day.inDisplayedMonth) {
            this.daySelected.emit(day.date);
        }
    }

    protected onBarClick(bar: HolidayBar, event: Event): void {
        // Without this the click reaches the day underneath and offers to create a holiday on top of this one.
        event.stopPropagation();
        this.holidaySelected.emit(bar.holiday);
    }
}
