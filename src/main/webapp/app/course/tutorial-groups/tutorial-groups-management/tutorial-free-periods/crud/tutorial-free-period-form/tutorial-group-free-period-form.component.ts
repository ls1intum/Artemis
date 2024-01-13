import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
// import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
// import {TutorialGroupFreePeriod} from "app/entities/tutorial-group/tutorial-group-free-day.model";

export interface TutorialGroupFreePeriodFormData {
    startDate?: Date;
    endDate?: Date;
    reason?: string;
}

export enum TimeFrame {
    Period,
    Day,
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
        reason: undefined,
    };

    @Input() isEditMode = false;

    @Input() timeZone: string;

    @Output() formSubmitted: EventEmitter<TutorialGroupFreePeriodFormData> = new EventEmitter<TutorialGroupFreePeriodFormData>();

    faCalendarAlt = faCalendarAlt;

    form: FormGroup;

    selectedTimeFrame = TimeFrame.Day;

    setSelectedTimeFrame(timeframe: TimeFrame) {
        this.selectedTimeFrame = timeframe;
    }

    // get periodControl() {
    //     return this.form.get('period');
    // }

    get dateControl() {
        return this.form.get('date');
    }

    get reasonControl() {
        return this.form.get('reason');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
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

    submitForm() {
        const tutorialGroupFreePeriodFormData: TutorialGroupFreePeriodFormData = { ...this.form.value };
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
            date: [undefined, [Validators.required]],
            reason: [undefined],
        });
    }

    markDateAsTouched() {
        if (this.dateControl) {
            this.dateControl.markAsTouched();
        }
    }

    get isDateInvalid() {
        if (this.dateControl) {
            return this.dateControl.invalid && (this.dateControl.touched || this.dateControl.dirty);
        } else {
            return false;
        }
    }
}
