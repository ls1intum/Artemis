import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, input, linkedSignal, output, viewChildren } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { buildMonthMatrix } from 'app/shared-ui/tum-ui/date-picker/tum-ui-date-picker.util';

/**
 * Hand-built month calendar grid for {@link TumUiDatePickerComponent}, part of the tum-aet-ui kit.
 * Angular has no CDK calendar, so the 6×7 Monday-first grid is built with dayjs. Day cells are native
 * buttons with roving-tabindex keyboard navigation (arrows/Home/End/PageUp-Down/Enter). Styled with tokens.
 */
@Component({
    selector: 'tum-ui-calendar',
    templateUrl: './tum-ui-calendar.component.html',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCalendarComponent {
    readonly selected = input<dayjs.Dayjs | undefined>(undefined);
    readonly activeMonth = input.required<dayjs.Dayjs>();
    readonly daySelected = output<dayjs.Dayjs>();
    readonly monthChange = output<dayjs.Dayjs>();

    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    protected readonly weeks = computed(() => buildMonthMatrix(this.activeMonth()));
    protected readonly flatDays = computed(() => this.weeks().flat());
    protected readonly weekdayLabels = computed(() => this.weeks()[0].map((day) => day.format('dd')));
    protected readonly monthLabel = computed(() => this.activeMonth().format('MMMM YYYY'));

    // Roving-tabindex focus. Derived from the grid + selection (resets when the month/selection changes) but
    // also writable imperatively from onKeydown — linkedSignal covers both without writing a signal in an effect.
    protected readonly focusedIndex = linkedSignal(() => {
        const days = this.flatDays();
        const target = this.selected() ?? this.activeMonth().startOf('month');
        const index = days.findIndex((day) => day.isSame(target, 'day'));
        return index >= 0 ? index : 0;
    });
    private readonly today = dayjs();
    private readonly dayButtons = viewChildren<ElementRef<HTMLButtonElement>>('dayButton');
    // Set when a month change was initiated from the keyboard (PageUp/PageDown), so we can restore DOM
    // focus to the roving cell after the grid re-renders. Mouse-driven prev/next must NOT steal focus.
    private restoreFocusAfterRender = false;

    constructor() {
        // A keyboard month change destroys the focused <button> (tracked by day.valueOf()), dropping focus
        // to <body> and breaking arrow-key navigation. After the new grid renders, move focus back to the
        // roving cell — but only when the change came from the keyboard.
        afterRenderEffect(() => {
            this.flatDays();
            if (this.restoreFocusAfterRender) {
                this.restoreFocusAfterRender = false;
                this.dayButtons()[this.focusedIndex()]?.nativeElement.focus();
            }
        });
    }

    /** Full class list for a day cell: exactly one text color per state so no two color utilities collide. */
    protected dayButtonClasses(day: dayjs.Dayjs): string {
        // `appearance-none border-0` + an explicit bg per state resets the native grey button-face (Artemis
        // ships no Tailwind preflight), so day cells render as clean circles rather than 3D grey boxes.
        const base =
            'appearance-none border-0 h-8 w-8 rounded-full hover:bg-surface-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-700';
        let color: string;
        if (this.isSelected(day)) {
            color = 'bg-primary text-surface-0';
        } else if (this.isOtherMonth(day)) {
            color = 'bg-transparent text-surface-400';
        } else {
            color = 'bg-transparent text-surface-900 dark:text-surface-0';
        }
        const today = this.isToday(day) && !this.isSelected(day) ? 'ring-1 ring-primary' : '';
        return `${base} ${color} ${today}`.trim();
    }

    protected isSelected(day: dayjs.Dayjs): boolean {
        const selected = this.selected();
        return !!selected && selected.isSame(day, 'day');
    }

    protected isToday(day: dayjs.Dayjs): boolean {
        return day.isSame(this.today, 'day');
    }

    protected isOtherMonth(day: dayjs.Dayjs): boolean {
        return day.month() !== this.activeMonth().month();
    }

    protected previousMonth(): void {
        this.monthChange.emit(this.activeMonth().subtract(1, 'month'));
    }

    protected nextMonth(): void {
        this.monthChange.emit(this.activeMonth().add(1, 'month'));
    }

    protected selectDay(day: dayjs.Dayjs): void {
        this.daySelected.emit(day);
    }

    protected onKeydown(event: KeyboardEvent, index: number): void {
        const total = this.flatDays().length;
        const moveTo = (target: number): void => {
            event.preventDefault();
            const clamped = Math.max(0, Math.min(total - 1, target));
            this.focusedIndex.set(clamped);
            this.dayButtons()[clamped]?.nativeElement.focus();
        };
        switch (event.key) {
            case 'ArrowRight':
                moveTo(index + 1);
                break;
            case 'ArrowLeft':
                moveTo(index - 1);
                break;
            case 'ArrowDown':
                moveTo(index + 7);
                break;
            case 'ArrowUp':
                moveTo(index - 7);
                break;
            case 'Home':
                moveTo(index - (index % 7));
                break;
            case 'End':
                moveTo(index - (index % 7) + 6);
                break;
            case 'Enter':
            case ' ':
                event.preventDefault();
                this.selectDay(this.flatDays()[index]);
                break;
            case 'PageUp':
                event.preventDefault();
                this.restoreFocusAfterRender = true;
                this.previousMonth();
                break;
            case 'PageDown':
                event.preventDefault();
                this.restoreFocusAfterRender = true;
                this.nextMonth();
                break;
        }
    }
}
