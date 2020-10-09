import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LearningGoal } from 'app/entities/learning-goal.model';

@Component({
    selector: 'jhi-learning-goal-form',
    templateUrl: './learning-goal-form.component.html',
    styles: [],
})
export class LearningGoalFormComponent implements OnInit, OnChanges {
    @Input()
    learningGoal: LearningGoal;

    @Output()
    submitLearningGoal: EventEmitter<LearningGoal> = new EventEmitter<LearningGoal>();

    learningGoalForm: FormGroup;

    constructor(private fb: FormBuilder) {}

    ngOnChanges(): void {
        this.initForm();
        this.setFormValues(this.learningGoal);
    }

    ngOnInit(): void {
        this.initForm();
    }

    private setFormValues(learningGoal: LearningGoal): void {
        this.learningGoalForm.patchValue(learningGoal);
    }

    initForm(): void {
        if (this.learningGoalForm) {
            return;
        }
        this.learningGoalForm = this.fb.group({
            title: ['', [Validators.required, Validators.minLength(3)]],
            description: [''],
        });
    }

    submitForm(): void {
        const formValue = this.learningGoalForm.value;
        const newLearningGoal: LearningGoal = {
            ...this.learningGoal,
            ...formValue,
        };

        this.submitLearningGoal.emit(newLearningGoal);
        this.learningGoalForm.reset();
    }
}
