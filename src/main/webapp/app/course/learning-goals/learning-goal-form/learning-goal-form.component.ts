import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

export interface LearningGoalFormData {
    title?: string;
    description?: string;
}

@Component({
    selector: 'jhi-learning-goal-form',
    templateUrl: './learning-goal-form.component.html',
    styles: [],
})
export class LearningGoalFormComponent implements OnInit, OnChanges {
    @Input()
    formData: LearningGoalFormData = {
        title: undefined,
        description: undefined,
    };

    @Input()
    isEditMode = false;
    @Output()
    formSubmitted: EventEmitter<LearningGoalFormData> = new EventEmitter<LearningGoalFormData>();

    form: FormGroup;
    // not included in reactive form
    description: string | undefined;

    constructor(private fb: FormBuilder) {}

    get titleControl() {
        return this.form.get('title');
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            title: [undefined, [Validators.required, Validators.maxLength(255)]],
            releaseDate: [undefined],
        });
    }

    private setFormValues(formData: LearningGoalFormData) {
        this.form.patchValue(formData);
        this.description = formData.description;
    }

    submitForm() {
        const learningGoalFormData: LearningGoalFormData = { ...this.form.value };
        learningGoalFormData.description = this.description;
        this.formSubmitted.emit(learningGoalFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }
}
