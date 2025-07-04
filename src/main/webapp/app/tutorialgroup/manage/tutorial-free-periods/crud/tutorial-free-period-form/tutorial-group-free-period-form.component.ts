import { ChangeDetectionStrategy, Component, EventEmitter, OnChanges, OnInit, Output, inject, input } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { OWL_DATE_TIME_FORMATS, OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';

export const MY_NATIVE_FORMATS = {
    datePickerInput: { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' },
    timePickerInput: { hour: 'numeric', minute: 'numeric' },
};

export interface TutorialGroupFreePeriodFormData {
    startDate?: Date;
    endDate?: Date;
    startTime?: Date;
    endTime?: Date;
    reason?: string;
}

/**
 * Enum for representing different time frames.
 * @enum {number}
 * @property {number} Day - Represents a whole day.
 * @property {number} Period - Represents a period spanning multiple days.
 * @property {number} PeriodWithinDay - Represents a period within a single day.
 */
export enum TimeFrame {
    Day,
    Period,
    PeriodWithinDay,
}

@Component({
    selector: 'jhi-tutorial-free-period-form',
    templateUrl: './tutorial-group-free-period-form.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [{ provide: OWL_DATE_TIME_FORMATS, useValue: MY_NATIVE_FORMATS }],
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, OwlDateTimeModule, ArtemisDatePipe, ArtemisTranslatePipe, FormDateTimePickerComponent],
})
export class TutorialGroupFreePeriodFormComponent implements OnInit, OnChanges {
    private fb = inject(FormBuilder);
    protected readonly DateTimePickerType = DateTimePickerType;

    readonly formData = input<TutorialGroupFreePeriodFormData>({
        startDate: undefined,
        endDate: undefined,
        startTime: undefined,
        endTime: undefined,
        reason: undefined,
    });

    readonly isEditMode = input(false);

    readonly timeZone = input<string>();

    @Output() formSubmitted: EventEmitter<TutorialGroupFreePeriodFormData> = new EventEmitter<TutorialGroupFreePeriodFormData>();

    faCalendarAlt = faCalendarAlt;

    form: FormGroup;
    // TimeFrame to store the current time frame of the form.
    protected timeFrame = TimeFrame.Day;

    // Enum Object to be used for Comparing different TimeFrames in the template.
    protected readonly TimeFrame = TimeFrame;

    /**
     * Sets the time frame for the form and resets the necessary date controls.
     * @param {TimeFrame} timeFrame - The time frame to set. This should be one of the values from the TimeFrame enum.
     */
    setTimeFrame(timeFrame: TimeFrame) {
        const resetControls = ['endDate', 'endTime', 'startTime'];
        resetControls.forEach((control) => {
            if (timeFrame === TimeFrame.Day || (timeFrame === TimeFrame.Period && control !== 'endDate') || (timeFrame === TimeFrame.PeriodWithinDay && control === 'endDate')) {
                this.resetDateControl(control);
            }
        });
        this.timeFrame = timeFrame;
    }

    /**
     * Resets the specified date-value in the form.
     * @param {string} controlName - The name of the form control to reset.
     */
    private resetDateControl(controlName: string) {
        const control = this.form.get(controlName);
        if (control) {
            control.reset();
            control.markAsUntouched();
        }
    }

    /**
     * Checks if the start date/time is before the end date/time based on the current time frame.
     * @returns {boolean} - Returns true if the start time/date is before the end time/date, otherwise returns true.
     */
    get isStartBeforeEnd(): boolean {
        if (this.timeFrame === TimeFrame.PeriodWithinDay && this.endTimeControl?.value && this.startTimeControl?.value) {
            return this.normalizeAndCompare(this.startTimeControl.value, this.endTimeControl.value, 'minute');
        }

        if (this.timeFrame === TimeFrame.Period && this.endDateControl?.value && this.startDateControl?.value) {
            return this.normalizeAndCompare(this.startDateControl.value, this.endDateControl.value, 'day');
        }

        return true;
    }

    /**
     * Normalize two input values (either Date or dayjs) to dayjs, round down to the chosen unit,
     * then check whether endValue > startValue.
     */
    private normalizeAndCompare(rawStart: Date | dayjs.Dayjs, rawEnd: Date | dayjs.Dayjs, unit: 'minute' | 'day'): boolean {
        const start = dayjs(rawStart).startOf(unit);
        const end = dayjs(rawEnd).startOf(unit);
        return end.isAfter(start);
    }

    get timeFrameControl(): TimeFrame {
        return this.timeFrame;
    }

    get startDateControl() {
        return this.form.get('startDate');
    }

    get endDateControl() {
        return this.form.get('endDate');
    }

    get startTimeControl() {
        return this.form.get('startTime');
    }

    get endTimeControl() {
        return this.form.get('endTime');
    }

    get reasonControl() {
        return this.form.get('reason');
    }

    /**
     * This getter method checks if the form submission is possible based on the validity of the form controls and the selected time frame.
     * @returns {boolean} - Returns true if the form can be submitted, false otherwise
     */
    get isSubmitPossible(): boolean {
        if (!this.startDateControl?.value || !this.startDateControl?.valid) {
            return false;
        }
        if (this.timeFrame === TimeFrame.Day) {
            return true;
        } else if (this.timeFrame === TimeFrame.Period) {
            return !!this.endDateControl?.value && !!this.endDateControl?.valid && this.isStartBeforeEnd;
        } else if (this.timeFrame === TimeFrame.PeriodWithinDay) {
            return (
                !!this.startTimeControl?.value &&
                !!this.startTimeControl?.valid &&
                !!this.endTimeControl?.value &&
                !!this.endTimeControl?.valid &&
                this.isStartBeforeEnd &&
                !this.isStartTimeInvalid &&
                !this.isEndTimeInvalid
            );
        }
        return false;
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    ngOnChanges() {
        this.initializeForm();
        const formData = this.formData();
        if (this.isEditMode() && formData) {
            this.setFormValues(formData);
            this.setFirstTimeFrameInEditMode(formData);
        }
    }

    submitForm() {
        const formValue = this.form.value;
        // Creating a TutorialGroupFreePeriodFormData is currently neccessary till component gets rewritten to modern angular
        const tutorialGroupFreePeriodFormData: TutorialGroupFreePeriodFormData = {
            startDate: formValue.startDate ? new Date(formValue.startDate) : undefined,
            endDate: formValue.endDate ? new Date(formValue.endDate) : undefined,
            startTime: formValue.startTime ? new Date(formValue.startTime) : undefined,
            endTime: formValue.endTime ? new Date(formValue.endTime) : undefined,
            reason: formValue.reason,
        };
        this.formSubmitted.emit(tutorialGroupFreePeriodFormData);
    }

    /**
     * Sets the form values based on the provided form data.
     * @param {TutorialGroupFreePeriodFormData} formData - The form data to set.
     */
    private setFormValues(formData: TutorialGroupFreePeriodFormData) {
        this.form.patchValue({
            startDate: formData.startDate,
            endDate: formData.endDate,
            startTime: formData.startTime,
            endTime: formData.endTime,
            reason: formData.reason,
        });
    }

    /**
     * Determines and sets the initial time frame when the form is in edit mode based on the provided form data.
     * @param formData The form data used to determine the initial time frame.
     */
    private setFirstTimeFrameInEditMode(formData: TutorialGroupFreePeriodFormData) {
        if (!formData.endDate && !formData.startTime && !formData.endTime) {
            this.setTimeFrame(TimeFrame.Day);
        } else if (!formData.endDate && formData.startTime && formData.endTime) {
            this.setTimeFrame(TimeFrame.PeriodWithinDay);
        } else {
            this.setTimeFrame(TimeFrame.Period);
        }
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            startDate: [undefined],
            endDate: [undefined],
            startTime: [undefined],
            endTime: [undefined],
            reason: [undefined],
        });
    }

    get isStartTimeInvalid() {
        if (this.startTimeControl) {
            return this.startTimeControl.invalid && (this.startTimeControl.touched || this.startTimeControl.dirty);
        }
        return false;
    }

    get isEndTimeInvalid() {
        if (this.endTimeControl) {
            return this.endTimeControl.invalid && (this.endTimeControl.touched || this.endTimeControl.dirty);
        }
        return false;
    }
}
