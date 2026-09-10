import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import {
    TumUiButtonDirective,
    TumUiDatePickerComponent,
    TumUiDialogComponent,
    TumUiFormFieldComponent,
    TumUiInputDirective,
    TumUiMessageComponent,
    TumUiToggleSwitchComponent,
} from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { HolidayOccurrence } from 'app/tutorialgroup/manage/holidays/holiday.model';

/** What the dialog hands back on save. Times are absent for a whole-day holiday. */
export interface HolidaySubmission {
    readonly day: dayjs.Dayjs;
    readonly wholeDay: boolean;
    readonly startTime?: string;
    readonly endTime?: string;
    readonly reason: string;
}

/** Where a partial-day holiday starts and ends when the reader first turns "whole day" off. */
const DEFAULT_START_TIME = '09:00';
const DEFAULT_END_TIME = '12:00';
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/**
 * Creates and edits a single holiday.
 *
 * One dialog covers both, because the fields are identical and only the heading and the confirming button differ. It
 * deliberately offers a single day rather than a span: a holiday reads as "this day is cancelled", and a term break is
 * entered as the handful of days it actually covers, which is also what the list and the calendar show.
 */
@Component({
    selector: 'jhi-holiday-dialog',
    templateUrl: './holiday-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiDatePickerComponent,
        TumUiDialogComponent,
        TumUiFormFieldComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
        TumUiToggleSwitchComponent,
    ],
})
export class HolidayDialogComponent {
    readonly visible = model(false);
    /** The holiday being edited, or undefined to create one. */
    readonly holiday = input<HolidayOccurrence | undefined>(undefined);
    /** Prefills the date when the reader opened the dialog by clicking an empty day. */
    readonly initialDay = input<dayjs.Dayjs | undefined>(undefined);
    /** Sessions scheduled on the chosen day, so the dialog can say what saving would cancel. */
    readonly sessionCountForSelectedDay = input(0);
    readonly saving = input(false);

    readonly save = output<HolidaySubmission>();
    /** The reader picked another date, so the page can load the session count for it. */
    readonly selectedDayChange = output<dayjs.Dayjs>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly day = signal<dayjs.Dayjs | undefined>(undefined);
    protected readonly wholeDay = signal(true);
    protected readonly startTime = signal(DEFAULT_START_TIME);
    protected readonly endTime = signal(DEFAULT_END_TIME);
    protected readonly reason = signal('');

    protected readonly isEditMode = computed(() => this.holiday() !== undefined);
    protected readonly weekdayLabel = computed(() => this.day()?.locale(this.locale()).format('dddd') ?? '');

    protected readonly timesAreValid = computed(() => {
        if (this.wholeDay()) {
            return true;
        }
        const start = this.startTime();
        const end = this.endTime();
        return TIME_PATTERN.test(start) && TIME_PATTERN.test(end) && start < end;
    });

    protected readonly canSave = computed(() => this.day() !== undefined && this.reason().trim().length > 0 && this.timesAreValid() && !this.saving());

    constructor() {
        // Reloads the form whenever the dialog opens, so a cancelled edit never leaks into the next one.
        effect(() => {
            if (!this.visible()) {
                return;
            }
            const occurrence = this.holiday();
            if (occurrence) {
                this.day.set(occurrence.day);
                this.wholeDay.set(occurrence.wholeDay);
                this.startTime.set(occurrence.startTime ?? DEFAULT_START_TIME);
                this.endTime.set(occurrence.endTime ?? DEFAULT_END_TIME);
                this.reason.set(occurrence.reason);
            } else {
                this.day.set(this.initialDay() ?? dayjs().startOf('day'));
                this.wholeDay.set(true);
                this.startTime.set(DEFAULT_START_TIME);
                this.endTime.set(DEFAULT_END_TIME);
                this.reason.set('');
            }
        });
    }

    protected onDayChange(value: dayjs.Dayjs | undefined): void {
        this.day.set(value);
        if (value) {
            this.selectedDayChange.emit(value);
        }
    }

    protected onSubmit(): void {
        const day = this.day();
        if (!day || !this.canSave()) {
            return;
        }
        this.save.emit({
            day,
            wholeDay: this.wholeDay(),
            startTime: this.wholeDay() ? undefined : this.startTime(),
            endTime: this.wholeDay() ? undefined : this.endTime(),
            reason: this.reason().trim(),
        });
    }

    protected onCancel(): void {
        this.visible.set(false);
    }
}
