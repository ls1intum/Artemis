import { Component, Input, OnInit } from '@angular/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Observable } from 'rxjs';
import { AthenaService } from 'app/assessment/athena.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-feedback-suggestion-options',
    templateUrl: './exercise-feedback-suggestion-options.component.html',
})
export class ExerciseFeedbackSuggestionOptionsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() readOnly: boolean = false;

    protected readonly AssessmentType = AssessmentType;

    readonly assessmentType: AssessmentType;

    isAthenaEnabled$: Observable<boolean>;
    modulesAvailable: boolean;
    availableAthenaModules: string[];

    constructor(
        private athenaService: AthenaService,
        private activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit(): void {
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.athenaService.getAvailableModules(courseId, this.exercise).subscribe((modules) => {
            this.availableAthenaModules = modules;
            this.modulesAvailable = modules.length > 0;
        });
        this.isAthenaEnabled$ = this.athenaService.isEnabled();
    }

    inputControlsDisabled() {
        if (this.exercise.type == ExerciseType.PROGRAMMING) {
            return this.exercise.assessmentType == AssessmentType.AUTOMATIC || this.readOnly;
        }
        return false;
    }

    getCheckboxLabelStyle() {
        if (this.exercise.type == ExerciseType.PROGRAMMING && this.exercise.assessmentType == AssessmentType.AUTOMATIC) {
            return { color: 'grey' };
        }
        return {};
    }

    toggleFeedbackSuggestions(event: any) {
        if (event.target.checked) {
            this.exercise.feedbackSuggestionModule = this.availableAthenaModules.first();
        } else {
            this.exercise.feedbackSuggestionModule = undefined;
        }
    }
}
