import { Component, computed, input, output, signal } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { TumAetUiButtonDirective, TumAetUiDatePickerComponent, TumAetUiDialogComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export interface DeimosDateRangeSelection {
    from: Dayjs;
    to: Dayjs;
}

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

@Component({
    selector: 'jhi-deimos-date-range-modal',
    templateUrl: './deimos-date-range-modal.component.html',
    imports: [TumAetUiDialogComponent, TumAetUiDatePickerComponent, TumAetUiButtonDirective, TumAetUiMessageComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class DeimosDateRangeModalComponent {
    titleTranslationKey = input.required<string>();
    maxWindowDays = input<number | undefined>(undefined);
    isSubmitting = input(false);

    confirmSelection = output<DeimosDateRangeSelection>();

    visible = signal(false);
    fromDate = signal<Dayjs | undefined>(undefined);
    toDate = signal<Dayjs | undefined>(undefined);
    // A picker keeps its last committed value while its text is not a date, so a submit must wait for the text to parse.
    readonly fromTextValid = signal(true);
    readonly toTextValid = signal(true);

    readonly hasRequiredDates = computed(() => !!this.fromDate() && !!this.toDate());

    readonly isOrderInvalid = computed(() => {
        const from = this.fromDate();
        const to = this.toDate();
        return !!from && !!to && from.isAfter(to);
    });

    readonly isWindowTooLarge = computed(() => {
        const maxDays = this.maxWindowDays();
        const from = this.fromDate();
        const to = this.toDate();
        if (!maxDays || !from || !to || this.isOrderInvalid()) {
            return false;
        }
        // Compared in elapsed milliseconds to match the server, which uses Duration.between(from, to) on instants.
        // dayjs' .diff(..., 'day', true) compensates for UTC-offset changes, so across a DST transition it reports one
        // hour less than the server measures. That let the client enable Submit for a window the server then rejected.
        const rangeInMilliseconds = to.valueOf() - from.valueOf();
        return rangeInMilliseconds > maxDays * MILLISECONDS_PER_DAY;
    });

    /** Ids of the messages that explain a rejected range, for the pickers to point at. */
    protected readonly describedBy = computed(() => (this.isOrderInvalid() ? 'deimos-order-error' : this.isWindowTooLarge() ? 'deimos-window-error' : undefined));

    readonly isSubmitDisabled = computed(
        () => !this.hasRequiredDates() || !this.fromTextValid() || !this.toTextValid() || this.isOrderInvalid() || this.isWindowTooLarge() || this.isSubmitting(),
    );

    open(defaultFrom?: Dayjs, defaultTo?: Dayjs): void {
        this.fromDate.set(defaultFrom);
        this.toDate.set(defaultTo);
        this.fromTextValid.set(true);
        this.toTextValid.set(true);
        this.visible.set(true);
    }

    cancel(): void {
        this.visible.set(false);
    }

    submit(): void {
        const from = this.fromDate();
        const to = this.toDate();
        if (this.isSubmitDisabled() || !from || !to) {
            return;
        }

        this.confirmSelection.emit({ from, to });
        this.visible.set(false);
    }
}
