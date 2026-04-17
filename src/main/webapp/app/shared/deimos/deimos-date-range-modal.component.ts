import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import dayjs, { Dayjs } from 'dayjs/esm';
import { DialogModule } from 'primeng/dialog';
import { DatePickerModule } from 'primeng/datepicker';
import { ButtonModule } from 'primeng/button';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export interface DeimosDateRangeSelection {
    from: Dayjs;
    to: Dayjs;
}

@Component({
    selector: 'jhi-deimos-date-range-modal',
    templateUrl: './deimos-date-range-modal.component.html',
    imports: [FormsModule, DialogModule, DatePickerModule, ButtonModule, TranslateDirective],
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
        return dayjs(this.fromDate()!).isAfter(dayjs(this.toDate()!));
    });

    readonly isWindowTooLarge = computed(() => {
        const maxDays = this.maxWindowDays();
        if (!maxDays || !this.hasRequiredDates() || this.isOrderInvalid()) {
            return false;
        }
        const rangeInDays = dayjs(this.toDate()!).diff(dayjs(this.fromDate()!), 'day', true);
        return rangeInDays > maxDays;
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
            from: dayjs(this.fromDate()!),
            to: dayjs(this.toDate()!),
        });
        this.visible.set(false);
    }
}
