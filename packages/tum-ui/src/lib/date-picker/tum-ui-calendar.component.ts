import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, input, linkedSignal, output, viewChildren } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { buildMonthMatrix } from './tum-ui-date-picker.util';
import { TUM_UI_TRANSLATOR } from '../i18n/tum-ui-translations';

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
    readonly focusOnInit = input(false);
    readonly daySelected = output<dayjs.Dayjs>();
    readonly monthChange = output<dayjs.Dayjs>();

    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    protected readonly weeks = computed(() => buildMonthMatrix(this.activeMonth()));
    protected readonly flatDays = computed(() => this.weeks().flat());
    protected readonly weekdayLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: 'short' })));
    protected readonly weekdayFullLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: 'long' })));
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
            const today = dayjs();
            return today.isSame(month, 'month') ? today : month.startOf('month');
        },
    });

    protected readonly focusedIndex = computed(() => {
        const days = this.flatDays();
        const index = days.findIndex((day) => day.isSame(this.focusedDate(), 'day'));
        return index >= 0 ? index : 0;
    });
    private readonly today = dayjs();
    private readonly dayButtons = viewChildren<ElementRef<HTMLButtonElement>>('dayButton');
    private focusedOnInit = false;
    private restoreFocusAfterRender = false;

    constructor() {
        afterRenderEffect(() => {
            this.flatDays();
            if (this.focusOnInit() && !this.focusedOnInit) {
                const initialButton = this.dayButtons()[this.focusedIndex()]?.nativeElement;
                if (initialButton) {
                    this.focusedOnInit = true;
                    initialButton.focus();
                }
            }
            if (this.restoreFocusAfterRender) {
                this.restoreFocusAfterRender = false;
                this.dayButtons()[this.focusedIndex()]?.nativeElement.focus();
            }
        });
    }

    protected dayButtonClasses(day: dayjs.Dayjs): string {
        const base =
            'tum:appearance-none tum:border-0 tum:h-8 tum:w-8 tum:rounded-full tum:hover:bg-tum-ui-hover-background tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-tum-ui-primary';
        let color: string;
        if (this.isSelected(day)) {
            color = 'tum:bg-tum-ui-primary tum:text-tum-ui-primary-contrast';
        } else if (this.isOtherMonth(day)) {
            color = 'tum:bg-transparent tum:text-tum-ui-muted';
        } else {
            color = 'tum:bg-transparent tum:text-tum-ui-text';
        }
        const today = this.isToday(day) && !this.isSelected(day) ? 'tum:ring-1 tum:ring-tum-ui-primary' : '';
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
            if (target >= 0 && target < total) {
                this.focusedDate.set(this.flatDays()[target]);
                this.dayButtons()[target]?.nativeElement.focus();
                return;
            }
            const targetDay = this.flatDays()[index].add(target - index, 'day');
            this.focusedDate.set(targetDay);
            this.restoreFocusAfterRender = true;
            this.monthChange.emit(targetDay.startOf('month'));
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
                this.monthChange.emit(
                    this.activeMonth()
                        .subtract(event.shiftKey ? 1 : 0, 'year')
                        .subtract(event.shiftKey ? 0 : 1, 'month'),
                );
                break;
            case 'PageDown':
                event.preventDefault();
                this.restoreFocusAfterRender = true;
                this.monthChange.emit(
                    this.activeMonth()
                        .add(event.shiftKey ? 1 : 0, 'year')
                        .add(event.shiftKey ? 0 : 1, 'month'),
                );
                break;
        }
    }
}
