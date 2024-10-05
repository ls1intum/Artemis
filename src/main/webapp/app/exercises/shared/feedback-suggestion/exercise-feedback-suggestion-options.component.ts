import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Observable } from 'rxjs';
import { AthenaService } from 'app/assessment/athena.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exercise-feedback-suggestion-options',
    templateUrl: './exercise-feedback-suggestion-options.component.html',
})
export class ExerciseFeedbackSuggestionOptionsComponent implements OnInit, OnChanges {
    private athenaService = inject(AthenaService);
    private activatedRoute = inject(ActivatedRoute);

    @Input() exercise: Exercise;
    @Input() dueDate?: dayjs.Dayjs;
    @Input() readOnly: boolean = false;

    protected readonly ExerciseType = ExerciseType;

    protected readonly AssessmentType = AssessmentType;

    readonly assessmentType: AssessmentType;

    isAthenaEnabled$: Observable<boolean>;
    modulesAvailable: boolean;
    availableAthenaModules: string[];
    initialAthenaModule?: string;

    ngOnInit(): void {
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.athenaService.getAvailableModules(courseId, this.exercise).subscribe((modules) => {
            this.availableAthenaModules = modules;
            this.modulesAvailable = modules.length > 0;
        });
        this.isAthenaEnabled$ = this.athenaService.isEnabled();
        this.initialAthenaModule = this.exercise.feedbackSuggestionModule;
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.dueDate && !changes.dueDate.isFirstChange()) {
            if (this.inputControlsDisabled()) {
                this.exercise.feedbackSuggestionModule = this.initialAthenaModule;
            }
        }
    }

    /**
     * Returns true in case the input controls should be disabled. This is the case for all exercises when the due date has passed. For programming exercises,
     * it returns true in case the assessment type is automatic, the exercise is readonly, the due date is undefined or the due date has passed.
     */
    inputControlsDisabled() {
        if (this.exercise.type == ExerciseType.PROGRAMMING) {
            return this.exercise.assessmentType == AssessmentType.AUTOMATIC || this.readOnly || this.exercise.dueDate == undefined || this.hasDueDatePassed();
        }
        return this.hasDueDatePassed();
    }

    /**
     * Returns the label style for the checkbox to enable feedback suggestions. In case the input controls are disabled, the label text color is set to grey.
     */
    getCheckboxLabelStyle() {
        if (this.inputControlsDisabled()) {
            return { color: 'grey' };
        }
        return {};
    }

    toggleFeedbackSuggestions(event: any) {
        if (event.target.checked) {
            this.exercise.feedbackSuggestionModule = this.availableAthenaModules.first();
        } else {
            this.exercise.allowFeedbackRequests = false;
            this.exercise.feedbackSuggestionModule = undefined;
        }
    }

    toggleFeedbackRequests(event: any) {
        if (event.target.checked) {
            this.exercise.feedbackSuggestionModule = this.availableAthenaModules.first();
            this.exercise.allowFeedbackRequests = true;
        } else {
            this.exercise.allowFeedbackRequests = false;
        }
    }

    private hasDueDatePassed() {
        return dayjs(this.exercise.dueDate).isBefore(dayjs());
    }
}
