import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
// import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';

export interface TutorialGroupFreePeriodFormData {
    startDate?: Date;
    endDate?: Date;
    // startTime?: Date;
    // endTime?: Date;
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
        // startTime: undefined,
        // endTime: undefined,
        reason: undefined,
    };

    @Input() isEditMode = false;

    @Input() timeZone: string;

    @Output() formSubmitted: EventEmitter<TutorialGroupFreePeriodFormData> = new EventEmitter<TutorialGroupFreePeriodFormData>();

    faCalendarAlt = faCalendarAlt;

    form: FormGroup;

    timeFrame = TimeFrame.Day;

    protected readonly TimeFrame = TimeFrame;

    // Todo: TimeFrame getter/setter. Reset endDate when switching back to single Day
    setTimeFrame(timeFrame: TimeFrame) {
        if (this.form.get('endDate') && timeFrame == TimeFrame.Day && this.formData.endDate != undefined) {
            this.form.get('endDate')?.reset();
            this.form.get('endDate')?.markAsUntouched();
        }
        this.timeFrame = timeFrame;
    }

    get isEndBeforeStart() {
        if (this.startDateControl && this.endDateControl) {
            return this.startDateControl.value > this.endDateControl.value;
        }
        return false;
    }

    getTimeFrame(): TimeFrame {
        return this.timeFrame;
    }

    get startDateControl() {
        return this.form.get('startDate');
    }

    get endDateControl() {
        return this.form.get('endDate');
    }

    // get startTimeControl() {
    //     return this.form.get('startTime');
    // }

    // get endTimeControl() {
    //     return this.form.get('endTime');
    // }

    get reasonControl() {
        return this.form.get('reason');
    }

    // ToDo: How can I validate the form for the three different timeFrames? This does not work.
    get isSubmitPossible() {
        if (this.form.get('startDate') == undefined || !this.form.get('startDate')?.touched) {
            return false;
        }

        if (this.timeFrame == TimeFrame.Day) {
            return this.form.get('startDate')?.touched && !this.isStartDateInvalid;
        } else if (this.timeFrame == TimeFrame.Period) {
            if (!this.endDateControl) {
                return false;
            }
            return !this.isStartDateInvalid && !this.isEndDateInvalid && !this.endDateControl.invalid && !this.isEndBeforeStart;
        } else {
            return false;
            // return !this.isStartDateInvalid && !this.isStartTimeInvalid && this.startTimeControl?.touched;
        }
    }

    constructor(private fb: FormBuilder) {}

    ngOnInit(): void {
        this.initializeForm();
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    // Todo: How can i submit a form with some empty fields? This does not work yet :/ Also, how can i merge the startTime with the date for the freePeriodWithinADay?
    submitForm() {
        const tutorialGroupFreePeriodFormData: TutorialGroupFreePeriodFormData = { ...this.form.value };
        if (this.timeFrame == TimeFrame.Day) {
            tutorialGroupFreePeriodFormData.endDate = undefined;
            // tutorialGroupFreePeriodFormData.endTime = undefined;
            tutorialGroupFreePeriodFormData.startDate = undefined;
        }
        this.formSubmitted.emit(tutorialGroupFreePeriodFormData);
    }

    private setFormValues(formData: TutorialGroupFreePeriodFormData) {
        this.form.patchValue(formData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            startDate: [undefined, [Validators.required]],
            endDate: [undefined, [Validators.required]],
            // startTime: [undefined, [Validators.required]],
            // endTime: [undefined, [Validators.required]],
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

    // markStartTimeAsTouched() {
    //     if (this.startTimeControl) {
    //         this.startTimeControl.markAsTouched();
    //     }
    // }

    // markEndTimeAsTouched() {
    //     if (this.endTimeControl) {
    //         this.endTimeControl.markAsTouched();
    //     }
    // }

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

    // get isStartTimeInvalid() {
    //     if (this.startTimeControl) {
    //         return this.startTimeControl.invalid && (this.startTimeControl.touched || this.startTimeControl.dirty);
    //     } else {
    //         return false;
    //     }
    // }

    // get isEndTimeInvalid() {
    //     if (this.endTimeControl) {
    //         // if (this.startTimeControl && this.startTimeControl.value.minutes >= this.endTimeControl.value.minutes) {
    //         //     return true;
    //         // }
    //         return this.endTimeControl.invalid && (this.endTimeControl.touched || this.endTimeControl.dirty);
    //     } else {
    //         return false;
    //     }
    // }
}
