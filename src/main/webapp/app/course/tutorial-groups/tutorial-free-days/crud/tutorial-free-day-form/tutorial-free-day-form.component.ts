import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-session/crud/tutorial-group-session-form/tutorial-group-session-form.component';

export interface TutorialGroupFreeDayFormData {
    date?: Date;
    reason?: string;
}
@Component({
    selector: 'jhi-tutorial-free-day-form',
    templateUrl: './tutorial-free-day-form.component.html',
    styleUrls: ['./tutorial-free-day-form.component.scss'],
})
export class TutorialFreeDayFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupFreeDayFormData = {
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
        const tutorialGroupFreeDayFormData: TutorialGroupFreeDayFormData = { ...this.form.value };
        this.formSubmitted.emit(tutorialGroupFreeDayFormData);
    }

    private setFormValues(formData: TutorialGroupFreeDayFormData) {
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
