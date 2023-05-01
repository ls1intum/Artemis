import { Component, EventEmitter, Input, OnInit, Output, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { LearningGoal, getIcon } from 'app/entities/learningGoal.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-learning-goal-selection',
    templateUrl: './learning-goal-selection.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: forwardRef(() => LearningGoalSelectionComponent),
        },
    ],
})
export class LearningGoalSelectionComponent implements OnInit, ControlValueAccessor {
    @Input() labelName: string;
    @Input() labelTooltip: string;
    @Input() value: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Input() learningGoals: LearningGoal[];

    @Output() valueChange = new EventEmitter();

    isLoading = false;

    getIcon = getIcon;
    faQuestionCircle = faQuestionCircle;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (value: any) => {};

    constructor(private route: ActivatedRoute, private courseStorageService: CourseStorageService, private learningGoalService: LearningGoalService) {}

    ngOnInit(): void {
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (this.learningGoals == undefined && courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            if (course?.learningGoals) {
                this.setLearningGoals(course.learningGoals!);
            } else {
                this.isLoading = true;
                this.learningGoalService.getAllForCourse(courseId).subscribe({
                    next: (response) => {
                        this.setLearningGoals(response.body!);
                        this.writeValue(this.value);
                        this.isLoading = false;
                    },
                    error: () => {
                        this.disabled = true;
                        this.isLoading = false;
                    },
                });
            }
        }
    }

    /**
     * Set the available learning goals for selection
     * @param learningGoals The learning goals of the course
     */
    setLearningGoals(learningGoals: LearningGoal[]) {
        this.learningGoals = learningGoals.map((learningGoal) => {
            // Remove unnecessary properties
            learningGoal.course = undefined;
            learningGoal.userProgress = undefined;
            return learningGoal;
        });
    }

    updateField(newValue: LearningGoal[]) {
        this.value = newValue;
        this._onChange(this.value);
        this.valueChange.emit();
    }

    writeValue(value?: LearningGoal[]): void {
        if (value && this.learningGoals) {
            // Compare the ids of the learning goals instead of the whole objects
            const ids = value.map((el) => el.id);
            this.value = this.learningGoals.filter((learningGoal) => ids.includes(learningGoal.id));
        } else {
            this.value = value;
        }
    }

    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    registerOnTouched(fn: any): void {}

    setDisabledState?(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }
}
