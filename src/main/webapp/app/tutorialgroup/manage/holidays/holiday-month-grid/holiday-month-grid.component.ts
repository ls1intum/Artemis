import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { getWeekdayNameKeys } from 'app/calendar/shared/util/calendar-util';
import { DAY_KEY_FORMAT, Holiday, coversLastDayFully, startsAtBeginningOfDay } from 'app/tutorialgroup/manage/holidays/holiday.model';

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

/**
 * The bar a holiday would occupy, drawn while the reader is choosing days rather than after they have.
 *
 * Carries the geometry of a real bar and none of its content: there is no reason to write yet, and no holiday to open.
 */
export interface HolidayPreviewBar {
    readonly startColumn: number;
    readonly span: number;
    readonly lane: number;
    readonly startsRun: boolean;
    readonly endsRun: boolean;
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
    /** Set only in the week the pointer is choosing days in. */
    readonly previewBar?: HolidayPreviewBar;
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
    // On the window rather than the grid: a drag released outside the calendar still has to end.
    host: {
        '(window:pointerup)': 'finishDrag()',
        '(window:pointercancel)': 'cancelDrag()',
    },
    imports: [FaIconComponent, TumUiButtonDirective, TranslateDirective, ArtemisTranslatePipe],
})
export class HolidayMonthGridComponent {
    /** Any day of the month to display; the grid derives the month from it. */
    readonly displayedMonth = input.required<dayjs.Dayjs>();
    readonly holidays = input.required<readonly Holiday[]>();
    readonly sessionCountsByDay = input.required<Map<string, number>>();
    /** Today in the course's time zone, so "today" is the course's day rather than the reader's. */
    readonly today = input.required<dayjs.Dayjs>();
    /**
     * The run a form is currently open for, kept previewed while it is filled in.
     *
     * A drag ends before the form opens, so without this the bar the form is anchored to would vanish the moment it
     * appeared. Holding it keeps the days under discussion on screen, which is the point of anchoring at all.
     */
    readonly selectedRange = input<{ start: dayjs.Dayjs; end: dayjs.Dayjs } | undefined>(undefined);

    readonly monthChange = output<dayjs.Dayjs>();
    readonly holidaySelected = output<{ holiday: Holiday; origin: HTMLElement }>();
    /** A click on a day, which opens the create dialog prefilled with that date. */
    readonly daySelected = output<{ day: dayjs.Dayjs; origin: HTMLElement }>();
    /**
     * A run of days picked by dragging across them, first day to last.
     *
     * An accelerator rather than the only way in: the dialog a single day opens carries an end date of its own, which
     * is what a reader who cannot drag uses to reach the same span.
     */
    readonly rangeSelected = output<{ start: dayjs.Dayjs; end: dayjs.Dayjs; origin: HTMLElement }>();

    private readonly gridElement = inject<ElementRef<HTMLElement>>(ElementRef);

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
        // The page speaks start/end; the clipping here speaks from/to. Converted once, rather than teaching the
        // geometry a second pair of names.
        const held = this.selectedRange();
        const preview = this.pendingRange() ?? (held ? { from: held.start, to: held.end } : undefined);

        const weeks: HolidayCalendarWeek[] = [];
        let weekStart = month.startOf('month').startOf('isoWeek');
        const gridEnd = month.endOf('month').endOf('isoWeek');

        while (weekStart.isBefore(gridEnd)) {
            const bars = this.barsForWeek(holidays, weekStart);
            // Placed after the real bars and through the same lane search, so a preview lands where the holiday would:
            // beside an existing one on a day they share rather than on top of it.
            const previewClip = preview && this.clipToWeek(preview.from, preview.to, weekStart);
            const previewBar: HolidayPreviewBar | undefined = previewClip
                ? {
                      startColumn: previewClip.startColumn,
                      span: previewClip.span,
                      startsRun: previewClip.startsRun,
                      endsRun: previewClip.endsRun,
                      lane: this.firstFreeLane(bars, previewClip.startColumn, previewClip.span),
                  }
                : undefined;
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

            // The preview counts towards the height like any other bar, so the week makes room for it rather than
            // letting it hang over the one below. A week holding no holiday does not change height at all: one lane
            // still fits inside the minimum every week already keeps.
            const laneCount = [...bars, ...(previewBar ? [previewBar] : [])].reduce((highest, bar) => Math.max(highest, bar.lane + 1), 0);
            weeks.push({
                id: days[0].dayKey,
                days,
                bars,
                previewBar,
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
    /**
     * Where a run of days falls inside one week, or undefined when it misses the week entirely.
     *
     * Shared by the holidays and by the preview, so a run being chosen is clipped to a week exactly as a saved one is
     * and the two cannot disagree about where a week boundary falls.
     */
    private clipToWeek(firstDay: dayjs.Dayjs, lastDay: dayjs.Dayjs, weekStart: dayjs.Dayjs) {
        const weekEnd = weekStart.add(DAYS_PER_WEEK - 1, 'day');
        if (lastDay.isBefore(weekStart, 'day') || firstDay.isAfter(weekEnd, 'day')) {
            return undefined;
        }
        const clipStart = firstDay.isBefore(weekStart, 'day') ? weekStart : firstDay;
        const clipEnd = lastDay.isAfter(weekEnd, 'day') ? weekEnd : lastDay;
        return {
            startColumn: clipStart.diff(weekStart, 'day'),
            span: clipEnd.diff(clipStart, 'day') + 1,
            startsRun: clipStart.isSame(firstDay, 'day'),
            endsRun: clipEnd.isSame(lastDay, 'day'),
        };
    }

    private barsForWeek(holidays: readonly Holiday[], weekStart: dayjs.Dayjs): HolidayBar[] {
        const bars: HolidayBar[] = [];

        for (const holiday of holidays) {
            const firstDay = holiday.start.startOf('day');
            const clipped = this.clipToWeek(firstDay, holiday.lastDay, weekStart);
            if (!clipped) {
                continue;
            }

            const { startColumn, span, startsRun, endsRun } = clipped;
            const startsPartway = startsRun && !startsAtBeginningOfDay(holiday.start);
            const endsPartway = endsRun && !coversLastDayFully(holiday.end);
            const clipStart = weekStart.add(startColumn, 'day');

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
    /** The day a drag began on, and the one the pointer is over now; both unset while no drag is in progress. */
    private readonly dragAnchor = signal<dayjs.Dayjs | undefined>(undefined);
    private readonly dragCurrent = signal<dayjs.Dayjs | undefined>(undefined);

    protected readonly isDragging = computed(() => this.dragAnchor() !== undefined);

    /** The run the pointer currently covers, ordered, so dragging backwards reads the same as dragging forwards. */
    private readonly pendingRange = computed<{ from: dayjs.Dayjs; to: dayjs.Dayjs } | undefined>(() => {
        const anchor = this.dragAnchor();
        const current = this.dragCurrent();
        if (!anchor || !current) {
            return undefined;
        }
        return anchor.isAfter(current) ? { from: current, to: anchor } : { from: anchor, to: current };
    });

    /** Days the drag would take, so the reader sees the span before releasing rather than after. */
    protected isInPendingRange(day: HolidayCalendarDay): boolean {
        const range = this.pendingRange();
        return !!range && !day.date.isBefore(range.from, 'day') && !day.date.isAfter(range.to, 'day');
    }

    protected onDayPointerDown(day: HolidayCalendarDay, event: PointerEvent): void {
        // Only the primary button: a right-click opens the browser's menu and must not leave a drag half-started.
        if (!day.inDisplayedMonth || event.button !== 0) {
            return;
        }
        // A touch is left to tap and edit. Following it would mean taking the pan gesture off the browser
        // (`touch-action: none`), and a calendar that can no longer be scrolled by dragging it is the worse trade: the
        // end date in the dialog a tap opens reaches the same span. Without a drag to start, the tap falls through to
        // the button's click exactly as before.
        if (event.pointerType === 'touch') {
            return;
        }
        // A pen captures the pointer to the element it went down on, so the enter events this drag follows would never
        // reach the other days and the range would stay stuck on its first one. Handing the capture back puts them on
        // whichever day is under the pointer. A mouse never takes the capture, so this is a no-op for it.
        const target = event.target as Element;
        if (target.hasPointerCapture?.(event.pointerId)) {
            target.releasePointerCapture(event.pointerId);
        }
        this.dragAnchor.set(day.date);
        this.dragCurrent.set(day.date);
    }

    protected onDayPointerEnter(day: HolidayCalendarDay): void {
        if (this.isDragging() && day.inDisplayedMonth) {
            this.dragCurrent.set(day.date);
        }
    }

    /**
     * Ends a drag wherever the pointer is released, which is why it listens on the window: letting go outside the
     * calendar would otherwise leave the grid believing a drag was still running.
     *
     * A drag that never left its day is left to the button's own click, so a plain click still opens the dialog on one
     * day. A drag across days ends on a different button, where a click is dispatched to their common ancestor rather
     * than to either of them - so nothing else fires and the range is the only thing reported.
     */
    protected finishDrag(): void {
        const range = this.pendingRange();
        this.dragAnchor.set(undefined);
        this.dragCurrent.set(undefined);
        if (range && !range.from.isSame(range.to, 'day')) {
            // Reported after the drag is cleared, so the preview the form anchors to is the held one the page hands
            // back rather than this drag's - which is about to disappear.
            this.rangeSelected.emit({ start: range.from, end: range.to, origin: this.previewElement() ?? this.gridElement.nativeElement });
        }
    }

    protected cancelDrag(): void {
        this.dragAnchor.set(undefined);
        this.dragCurrent.set(undefined);
    }

    protected onDayClick(day: HolidayCalendarDay, event: Event): void {
        if (day.inDisplayedMonth) {
            this.daySelected.emit({ day: day.date, origin: event.currentTarget as HTMLElement });
        }
    }

    protected onBarClick(bar: HolidayBar, event: Event): void {
        // Without this the click reaches the day underneath and offers to create a holiday on top of this one.
        event.stopPropagation();
        this.holidaySelected.emit({ holiday: bar.holiday, origin: event.currentTarget as HTMLElement });
    }

    /** The preview bar on screen, which is what a form about a run of days should point at. */
    private previewElement(): HTMLElement | undefined {
        return this.gridElement.nativeElement.querySelector<HTMLElement>('[data-testid="holiday-calendar-preview"]') ?? undefined;
    }
}
