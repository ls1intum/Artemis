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

// ToDo: TimeFrame Enum
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

// ToDo: extend TutorialGroupFreePeriodFormData to support an endDate
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

    protected timeFrame = TimeFrame.Day;

    protected readonly TimeFrame = TimeFrame;

    // Todo: TimeFrame getter/setter. Reset endDate when switching back to single Day
    setTimeFrame(timeFrame: TimeFrame) {
        if (timeFrame == TimeFrame.Day && this.formData.endDate != undefined) {
            this.resetDateControl('endDate');
            this.resetDateControl('endTime');
            this.resetDateControl('startTime');
        } else if (timeFrame == TimeFrame.Period) {
            this.resetDateControl('startTime');
            this.resetDateControl('endTime');
        } else if (timeFrame == TimeFrame.PeriodWithinDay) {
            this.resetDateControl('endDate');
        }
        this.timeFrame = timeFrame;
    }

    private resetDateControl(controlName: string) {
        if (this.form.get(controlName)) {
            this.form.get(controlName)?.reset();
            this.form.get(controlName)?.markAsUntouched();
        }
    }

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
        // debugger
        return this.timeFrame == TimeFrame.Day;
    }

    get isFreePeriod(): boolean {
        // debugger
        return this.timeFrame == TimeFrame.Period;
    }

    get isFreePeriodWithinDay(): boolean {
        // debugger
        return this.timeFrame == TimeFrame.PeriodWithinDay;
    }

    get TimeFrameControl(): TimeFrame {
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

        // if (TutorialGroupFreePeriodsManagementComponent.isFreeDay(tempFreePeriod)) {
        //     this.setTimeFrame(TimeFrame.Day);
        //     formData.endDate = undefined;
        //     formData.startTime = undefined;
        //     formData.endTime = undefined;
        // } else if (TutorialGroupFreePeriodsManagementComponent.isFreePeriod(tempFreePeriod)) {
        //     this.setTimeFrame(TimeFrame.Period);
        //     formData.startTime = undefined;
        //     formData.endTime = undefined;
        // } else if (TutorialGroupFreePeriodsManagementComponent.isFreePeriodWithinDay(tempFreePeriod)) {
        //     this.setTimeFrame(TimeFrame.PeriodWithinDay);
        //     formData.endDate = undefined;
        // }
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
