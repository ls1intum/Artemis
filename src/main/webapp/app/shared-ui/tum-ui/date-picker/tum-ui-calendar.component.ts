import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, signal, viewChildren } from '@angular/core';
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

    protected readonly focusedIndex = signal(0);
    private readonly today = dayjs();
    private readonly dayButtons = viewChildren<ElementRef<HTMLButtonElement>>('dayButton');

    constructor() {
        // Reset roving focus to the selected day (or the 1st of the month) whenever the grid changes.
        effect(() => {
            const days = this.flatDays();
            const target = this.selected() ?? this.activeMonth().startOf('month');
            const index = days.findIndex((day) => day.isSame(target, 'day'));
            this.focusedIndex.set(index >= 0 ? index : 0);
        });
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
                this.previousMonth();
                break;
            case 'PageDown':
                event.preventDefault();
                this.nextMonth();
                break;
        }
    }
}
