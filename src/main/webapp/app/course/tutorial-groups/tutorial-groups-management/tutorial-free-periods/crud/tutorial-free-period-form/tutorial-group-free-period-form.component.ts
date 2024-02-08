import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
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
})
export class TutorialGroupFreePeriodFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupFreePeriodFormData = {
        startDate: undefined,
        endDate: undefined,
        startTime: undefined,
        endTime: undefined,
        reason: undefined,
    };

    @Input() isEditMode = false;

    @Input() timeZone: string;

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
     * @param {string} controlName - The name of the control to reset.
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
    get isStartBeforeEnd() {
        if (this.timeFrame == TimeFrame.PeriodWithinDay && this.endTimeControl && this.startTimeControl) {
            return this.endTimeControl.value > this.startTimeControl.value;
        } else if (this.timeFrame == TimeFrame.Period && this.endDateControl && this.startDateControl) {
            return this.endDateControl.value > this.startDateControl.value;
        } else {
            return true;
        }
    }

    get isFreeDay(): boolean {
        return this.timeFrame == TimeFrame.Day;
    }

    get isFreePeriod(): boolean {
        return this.timeFrame == TimeFrame.Period;
    }

    get isFreePeriodWithinDay(): boolean {
        return this.timeFrame == TimeFrame.PeriodWithinDay;
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

    get isSubmitPossible() {
        if (!this.startDateControl || this.startDateControl.invalid) {
            return false;
        }

        if (this.timeFrame == TimeFrame.Day) {
            return !this.isStartDateInvalid;
        } else if (this.timeFrame == TimeFrame.Period) {
            if (!this.endDateControl) {
                return false;
            }
            return !this.isStartDateInvalid && !this.isEndDateInvalid && !this.endDateControl.invalid && this.isStartBeforeEnd;
        } else if (this.timeFrame == TimeFrame.PeriodWithinDay) {
            if (!this.startTimeControl || !this.endTimeControl) {
                return false;
            }
            return (
                !this.isStartDateInvalid &&
                !this.isStartTimeInvalid &&
                !this.isEndTimeInvalid &&
                !this.startTimeControl.invalid &&
                !this.endTimeControl.invalid &&
                this.isStartBeforeEnd
            );
        }
        return false;
    }

    constructor(private fb: FormBuilder) {}

    ngOnInit(): void {
        this.initializeForm();
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            // this.setFormValues(this.formData);
            this.form.patchValue({
                startDate: this.formData.startDate,
                endDate: this.formData.endDate || undefined,
                startTime: this.formData.startTime || undefined,
                endTime: this.formData.endTime || undefined,
                reason: this.formData.reason,
            });
            this.setFirstTimeFrameInEditMode(this.formData);
        }
    }

    submitForm() {
        const tutorialGroupFreePeriodFormData: TutorialGroupFreePeriodFormData = { ...this.form.value };
        this.formSubmitted.emit(tutorialGroupFreePeriodFormData);
    }

    private setFormValues(formData: TutorialGroupFreePeriodFormData) {
        this.form.patchValue(formData);
    }

    private setFirstTimeFrameInEditMode(formData: TutorialGroupFreePeriodFormData) {
        // const tempFreePeriod = this.createTutorialGroupFreePeriodFromFormData(formData);
        if (formData.endDate === undefined && formData.startTime === undefined && formData.endTime === undefined) {
            this.setTimeFrame(TimeFrame.Day);
        } else if (formData.endDate === undefined && formData.startTime !== undefined && formData.endTime !== undefined) {
            this.setTimeFrame(TimeFrame.PeriodWithinDay);
        } else {
            this.setTimeFrame(TimeFrame.Period);
        }
    }

    private createTutorialGroupFreePeriodFromFormData(formData: TutorialGroupFreePeriodFormData): TutorialGroupFreePeriod {
        const tutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        tutorialGroupFreePeriod.start = formData.startDate ? dayjs(formData.startDate) : undefined;
        tutorialGroupFreePeriod.end = formData.endDate ? dayjs(formData.endDate) : undefined;
        tutorialGroupFreePeriod.reason = formData.reason;
        return tutorialGroupFreePeriod;
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            startDate: [undefined, [Validators.required]],
            endDate: [undefined],
            startTime: [undefined],
            endTime: [undefined],
            reason: [undefined],
        });
    }

    markStartDateAsTouched() {
        if (this.startDateControl) {
            this.startDateControl.markAsTouched();
        }
    }

    markEndDateAsTouched() {
        if (this.endDateControl) {
            this.endDateControl.markAsTouched();
        }
    }

    markStartTimeAsTouched() {
        if (this.startTimeControl) {
            this.startTimeControl.markAsTouched();
        }
    }

    markEndTimeAsTouched() {
        if (this.endTimeControl) {
            this.endTimeControl.markAsTouched();
        }
    }

    get isStartDateInvalid() {
        if (this.startDateControl) {
            return this.startDateControl.invalid && (this.startDateControl.touched || this.startDateControl.dirty);
        } else {
            return false;
        }
    }

    get isEndDateInvalid() {
        if (this.endDateControl) {
            return this.endDateControl.invalid && (this.endDateControl.touched || this.endDateControl.dirty);
        } else {
            return false;
        }
    }

    get isStartTimeInvalid() {
        if (this.startTimeControl) {
            return this.startTimeControl.invalid && (this.startTimeControl.touched || this.startTimeControl.dirty);
        } else {
            return false;
        }
    }

    get isEndTimeInvalid() {
        if (this.endTimeControl) {
            // if (this.startTimeControl && this.startTimeControl.value.minutes >= this.endTimeControl.value.minutes) {
            //     return true;
            // }
            return this.endTimeControl.invalid && (this.endTimeControl.touched || this.endTimeControl.dirty);
        } else {
            return false;
        }
    }

    protected readonly TutorialGroupFreePeriodsManagementComponent = TutorialGroupFreePeriodsManagementComponent;
}
