import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, input, linkedSignal, output, viewChildren } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { buildMonthMatrix } from './tum-ui-date-picker.util';
import { TUM_UI_TRANSLATOR } from '../i18n/tum-ui-translations';

/**
 * Hand-built month calendar grid for {@link TumUiDatePickerComponent}.
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
    private readonly translator = inject(TUM_UI_TRANSLATOR);

    readonly selected = input<dayjs.Dayjs | undefined>(undefined);
    readonly activeMonth = input.required<dayjs.Dayjs>();
    readonly daySelected = output<dayjs.Dayjs>();
    readonly monthChange = output<dayjs.Dayjs>();

    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    protected readonly weeks = computed(() => buildMonthMatrix(this.activeMonth()));
    protected readonly flatDays = computed(() => this.weeks().flat());
    protected readonly weekdayLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: 'short' })));
    protected readonly monthLabel = computed(() => this.formatDate(this.activeMonth(), { month: 'long', year: 'numeric' }));
    protected readonly previousMonthLabel = computed(() =>
        this.translate('tumUi.datePicker.previousMonth', { month: this.formatDate(this.activeMonth().subtract(1, 'month'), { month: 'long', year: 'numeric' }) }),
    );
    protected readonly nextMonthLabel = computed(() =>
        this.translate('tumUi.datePicker.nextMonth', { month: this.formatDate(this.activeMonth().add(1, 'month'), { month: 'long', year: 'numeric' }) }),
    );

    protected readonly focusedDate = linkedSignal<{ month: dayjs.Dayjs; selected: dayjs.Dayjs | undefined }, dayjs.Dayjs>({
        source: () => ({ month: this.activeMonth(), selected: this.selected() }),
        computation: ({ month, selected }, previous) => {
            const previousSelected = previous?.source.selected;
            if (selected && (!previousSelected || !selected.isSame(previousSelected, 'day'))) {
                return selected;
            }
            if (previous) {
                return month.date(Math.min(previous.value.date(), month.daysInMonth()));
            }
            return month.startOf('month');
        },
    });

    protected readonly focusedIndex = computed(() => {
        const days = this.flatDays();
        const index = days.findIndex((day) => day.isSame(this.focusedDate(), 'day'));
        return index >= 0 ? index : 0;
    });
    private readonly today = dayjs();
    private readonly dayButtons = viewChildren<ElementRef<HTMLButtonElement>>('dayButton');
    private restoreFocusAfterRender = false;

    constructor() {
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
        const base =
            'appearance-none border-0 h-8 w-8 rounded-full hover:bg-tum-ui-surface-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-tum-ui-primary dark:hover:bg-tum-ui-surface-700';
        let color: string;
        if (this.isSelected(day)) {
            color = 'bg-tum-ui-primary text-tum-ui-surface-0';
        } else if (this.isOtherMonth(day)) {
            color = 'bg-transparent text-tum-ui-surface-400';
        } else {
            color = 'bg-transparent text-tum-ui-surface-900 dark:text-tum-ui-surface-0';
        }
        const today = this.isToday(day) && !this.isSelected(day) ? 'ring-1 ring-tum-ui-primary' : '';
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

    protected dayLabel(day: dayjs.Dayjs): string {
        return this.formatDate(day, { dateStyle: 'full' });
    }

    private formatDate(day: dayjs.Dayjs, options: Intl.DateTimeFormatOptions): string {
        this.translator.changes?.();
        return new Intl.DateTimeFormat(this.translator.locale?.(), options).format(day.toDate());
    }

    private translate(key: string, params: Readonly<Record<string, string>>): string {
        this.translator.changes?.();
        return this.translator.translate(key, params);
    }

    protected onKeydown(event: KeyboardEvent, index: number): void {
        const total = this.flatDays().length;
        const moveTo = (target: number): void => {
            event.preventDefault();
            const clamped = Math.max(0, Math.min(total - 1, target));
            this.focusedDate.set(this.flatDays()[clamped]);
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
            case ' ': {
                event.preventDefault();
                const day = this.flatDays()[index];
                if (this.isOtherMonth(day)) {
                    this.restoreFocusAfterRender = true;
                }
                this.selectDay(day);
                break;
            }
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
