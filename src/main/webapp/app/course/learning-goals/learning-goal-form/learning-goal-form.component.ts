import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { catchError, delay, map, switchMap } from 'rxjs/operators';

/**
 * Async Validator to make sure that a learning goal title is unique within a course
 */
export const titleUniqueValidator = (learningGoalService: LearningGoalService, courseId: number) => {
    return (learningGoalTitleControl: FormControl) => {
        return of(learningGoalTitleControl.value).pipe(
            delay(250),
            switchMap((title) => {
                return learningGoalService.getAllForCourse(courseId).pipe(
                    map((res) => {
                        let learningGoalTitles: string[] = [];
                        if (res.body) {
                            learningGoalTitles = res.body.map((learningGoal) => learningGoal.title!);
                        }
                        if (learningGoalTitles.includes(title)) {
                            return {
                                titleUnique: { valid: false },
                            };
                        } else {
                            return null;
                        }
                    }),
                    catchError(() => of(null)),
                );
            }),
        );
    };
};

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
    @Input()
    courseId: number;

    @Output()
    formSubmitted: EventEmitter<LearningGoalFormData> = new EventEmitter<LearningGoalFormData>();

    form: FormGroup;

    constructor(private fb: FormBuilder, private learningGoalService: LearningGoalService) {}

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
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
            title: [undefined, [Validators.required, Validators.maxLength(255)], [titleUniqueValidator(this.learningGoalService, this.courseId)]],
            description: [undefined, [Validators.maxLength(10000)]],
        });
    }

    private setFormValues(formData: LearningGoalFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const learningGoalFormData: LearningGoalFormData = { ...this.form.value };
        this.formSubmitted.emit(learningGoalFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }
}
