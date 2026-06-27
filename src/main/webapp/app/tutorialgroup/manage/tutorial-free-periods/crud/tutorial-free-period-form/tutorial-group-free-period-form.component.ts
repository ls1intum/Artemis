import { ChangeDetectionStrategy, Component, OnInit, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';

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
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, ArtemisDatePipe, ArtemisTranslatePipe, FormDateTimePickerComponent],
})
export class TutorialGroupFreePeriodFormComponent implements OnInit {
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

    readonly formSubmitted = output<TutorialGroupFreePeriodFormData>();

    faCalendarAlt = faCalendarAlt;

    form: FormGroup;
    // TimeFrame to store the current time frame of the form.
    protected readonly timeFrame = signal(TimeFrame.Day);

    // Enum Object to be used for Comparing different TimeFrames in the template.
    protected readonly TimeFrame = TimeFrame;

    constructor() {
        // Effect to handle formData changes (replaces ngOnChanges)
        effect(() => {
            const formData = this.formData();
            const editMode = this.isEditMode();
            this.initializeForm();
            if (editMode && formData) {
                this.setFormValues(formData);
                this.setFirstTimeFrameInEditMode(formData);
            }
        });
    }

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
        this.timeFrame.set(timeFrame);
        // In edit mode, a prior tab switch may have cleared controls that are relevant to the
        // newly-selected timeFrame (e.g. switching Day→Period leaves endDate empty because
        // setTimeFrame(Day) reset it). Restore the original formData value for any such control
        // so the user sees the pre-existing data again when switching back.
        if (this.isEditMode()) {
            this.restoreVisibleControlsFromFormData(timeFrame);
        }
    }

    /**
     * For each control that is shown in the new timeFrame, if the control is currently empty
     * (it was cleared by a prior tab switch), restore the original formData value.
     * Controls the user intentionally cleared are left alone (guard: only restore when empty).
     */
    private restoreVisibleControlsFromFormData(timeFrame: TimeFrame) {
        const formData = this.formData();
        const restoreIfEmpty = (controlName: string, originalValue: Date | undefined) => {
            const control = this.form.get(controlName);
            if (control && !control.value && originalValue) {
                control.setValue(originalValue);
            }
        };
        restoreIfEmpty('startDate', formData.startDate);
        if (timeFrame === TimeFrame.Period) {
            restoreIfEmpty('endDate', formData.endDate);
        } else if (timeFrame === TimeFrame.PeriodWithinDay) {
            restoreIfEmpty('startTime', formData.startTime);
            restoreIfEmpty('endTime', formData.endTime);
        }
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
        if (this.timeFrame() === TimeFrame.PeriodWithinDay && this.endTimeControl?.value && this.startTimeControl?.value) {
            return this.normalizeAndCompare(this.startTimeControl.value, this.endTimeControl.value, 'minute');
        }

        if (this.timeFrame() === TimeFrame.Period && this.endDateControl?.value && this.startDateControl?.value) {
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
        return this.timeFrame();
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
        if (this.timeFrame() === TimeFrame.Day) {
            return true;
        } else if (this.timeFrame() === TimeFrame.Period) {
            return !!this.endDateControl?.value && !!this.endDateControl?.valid && this.isStartBeforeEnd;
        } else if (this.timeFrame() === TimeFrame.PeriodWithinDay) {
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
