import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';

export interface TutorialGroupFreePeriodFormData {
    date?: Date;
    reason?: string;
}
@Component({
    selector: 'jhi-tutorial-free-period-form',
    templateUrl: './tutorial-group-free-period-form.component.html',
})
export class TutorialGroupFreePeriodFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupFreePeriodFormData = {
        date: undefined,
        reason: undefined,
    };

    @Input() isEditMode = false;

    @Output() formSubmitted: EventEmitter<TutorialGroupSessionFormData> = new EventEmitter<TutorialGroupSessionFormData>();

    faCalendarAlt = faCalendarAlt;

    form: FormGroup;

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
}
