import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import dayjs, { Dayjs } from 'dayjs/esm';
import { DialogModule } from 'primeng/dialog';
import { DatePickerModule } from 'primeng/datepicker';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export interface DeimosDateRangeSelection {
    from: Dayjs;
    to: Dayjs;
}

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

@Component({
    selector: 'jhi-deimos-date-range-modal',
    templateUrl: './deimos-date-range-modal.component.html',
    imports: [FormsModule, DialogModule, DatePickerModule, ButtonModule, MessageModule, TranslateDirective],
})
export class DeimosDateRangeModalComponent {
    titleTranslationKey = input.required<string>();
    maxWindowDays = input<number | undefined>(undefined);
    isSubmitting = input(false);

    confirmSelection = output<DeimosDateRangeSelection>();

    visible = signal(false);
    fromDate = signal<Date | undefined>(undefined);
    toDate = signal<Date | undefined>(undefined);

    readonly hasRequiredDates = computed(() => !!this.fromDate() && !!this.toDate());

    readonly isOrderInvalid = computed(() => {
        if (!this.hasRequiredDates()) {
            return false;
        }
        return dayjs(this.fromDate()).isAfter(dayjs(this.toDate()));
    });

    readonly isWindowTooLarge = computed(() => {
        const maxDays = this.maxWindowDays();
        if (!maxDays || !this.hasRequiredDates() || this.isOrderInvalid()) {
            return false;
        }
        // Compared in elapsed milliseconds to match the server, which uses Duration.between(from, to) on instants.
        // dayjs' .diff(..., 'day', true) compensates for UTC-offset changes, so across a DST transition it reports one
        // hour less than the server measures. That let the client enable Submit for a window the server then rejected.
        const rangeInMilliseconds = dayjs(this.toDate()).valueOf() - dayjs(this.fromDate()).valueOf();
        return rangeInMilliseconds > maxDays * MILLISECONDS_PER_DAY;
    });

    readonly isSubmitDisabled = computed(() => !this.hasRequiredDates() || this.isOrderInvalid() || this.isWindowTooLarge() || this.isSubmitting());

    open(defaultFrom?: Dayjs, defaultTo?: Dayjs): void {
        this.fromDate.set(defaultFrom?.toDate());
        this.toDate.set(defaultTo?.toDate());
        this.visible.set(true);
    }

    cancel(): void {
        this.visible.set(false);
    }

    submit(): void {
        if (this.isSubmitDisabled()) {
            return;
        }

        this.confirmSelection.emit({
            from: dayjs(this.fromDate()),
            to: dayjs(this.toDate()),
        });
        this.visible.set(false);
    }
}
