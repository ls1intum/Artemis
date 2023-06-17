import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { LearningGoal, getIcon } from 'app/entities/learningGoal.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

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
export class LearningGoalSelectionComponent implements OnInit, OnChanges, ControlValueAccessor {
    @Input() labelName: string;
    @Input() labelTooltip: string;
    @Input() value: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Input() learningGoals: LearningGoal[];
    nonOptionalLearningGoals: LearningGoal[];
    selectableLearningGoals: LearningGoal[];
    @Input() includeOptionals: boolean;

    @Output() valueChange = new EventEmitter();

    isLoading = false;

    getIcon = getIcon;
    faQuestionCircle = faQuestionCircle;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (value: any) => {};

    constructor(private route: ActivatedRoute, private courseStorageService: CourseStorageService, private learningGoalService: LearningGoalService) {}

    ngOnInit(): void {
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (!this.learningGoals && courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            if (course?.competencies) {
                this.setLearningGoals(course.competencies!);
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
        } else {
            this.setLearningGoals(this.learningGoals);
        }
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.includeOptionals.currentValue !== changes.includeOptionals.previousValue) {
            if (changes.includeOptionals.currentValue) {
                this.selectableLearningGoals = this.learningGoals;
            } else {
                this.selectableLearningGoals = this.nonOptionalLearningGoals;
            }
        }
    }

    /**
     * Set the available competencies for selection
     * @param learningGoals The competencies of the course
     */
    setLearningGoals(learningGoals: LearningGoal[]) {
        this.learningGoals = learningGoals.map((learningGoal) => {
            // Remove unnecessary properties
            learningGoal.course = undefined;
            learningGoal.userProgress = undefined;
            return learningGoal;
        });

        this.nonOptionalLearningGoals = this.learningGoals.filter((learningGoal) => !learningGoal.optional);
        this.selectableLearningGoals = this.includeOptionals ? this.learningGoals : this.nonOptionalLearningGoals;
    }

    updateField(newValue: LearningGoal[]) {
        this.value = newValue;
        this._onChange(this.value);
        this.valueChange.emit();
    }

    writeValue(value?: LearningGoal[]): void {
        if (value && this.selectableLearningGoals) {
            // Compare the ids of the competencies instead of the whole objects
            const ids = value.map((el) => el.id);
            this.value = this.selectableLearningGoals.filter((learningGoal) => ids.includes(learningGoal.id));
        } else {
            this.value = value;
        }
    }

    updateIncludeOptionals(includedInOverallScore: IncludedInOverallScore | undefined) {
        if (!includedInOverallScore) {
            return;
        }
        this.includeOptionals = includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY;
        if (!this.includeOptionals) {
            this.updateField(this.value?.filter((learningGoal: LearningGoal) => !learningGoal.optional));
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
