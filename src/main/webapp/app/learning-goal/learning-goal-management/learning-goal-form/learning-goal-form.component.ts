import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LearningGoal } from 'app/entities/learning-goal.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-learning-goal-form',
    templateUrl: './learning-goal-form.component.html',
    styles: [],
})
export class LearningGoalFormComponent implements OnInit, OnChanges {
    @Input()
    dialogError: Observable<string>;

    @Input()
    learningGoal: LearningGoal;

    @Input()
    editMode = false;

    @Output()
    submitLearningGoal: EventEmitter<LearningGoal> = new EventEmitter<LearningGoal>();

    @Output()
    deleteLearningGoal: EventEmitter<LearningGoal> = new EventEmitter<LearningGoal>();

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

    submitDelete(): void {
        this.deleteLearningGoal.emit(this.learningGoal);
        this.learningGoalForm.reset();
    }
}
