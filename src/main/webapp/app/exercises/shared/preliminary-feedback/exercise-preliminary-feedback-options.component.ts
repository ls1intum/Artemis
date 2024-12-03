import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Observable } from 'rxjs';
import { AthenaService } from 'app/assessment/athena.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exercise-preliminary-feedback-options',
    templateUrl: './exercise-preliminary-feedback-options.component.html',
})
export class ExercisePreliminaryFeedbackOptionsComponent implements OnInit, OnChanges {
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
    showDropdownList: boolean = false;

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
     * Returns true in case the input controls should be disabled. This is the case for all exercises when the due date has passed.
     */
    inputControlsDisabled() {
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

    togglePreliminaryFeedback(event: any) {
        this.showDropdownList = event.target.checked;
        if (event.target.checked) {
            this.exercise.preliminaryFeedbackModule = this.availableAthenaModules.first();
        } else {
            this.exercise.preliminaryFeedbackModule = '';
        }
    }

    private hasDueDatePassed() {
        return dayjs(this.exercise.dueDate).isBefore(dayjs());
    }
}
