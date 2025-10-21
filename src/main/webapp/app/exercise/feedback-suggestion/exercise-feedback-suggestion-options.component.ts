import { AfterViewChecked, ChangeDetectorRef, Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgStyle } from '@angular/common';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FormsModule } from '@angular/forms';
import { AthenaModuleMode } from 'app/assessment/shared/entities/athena.model';

@Component({
    selector: 'jhi-exercise-feedback-suggestion-options',
    templateUrl: './exercise-feedback-suggestion-options.component.html',
    imports: [TranslateDirective, NgStyle, HelpIconComponent, FormsModule],
})
export class ExerciseFeedbackSuggestionOptionsComponent implements OnInit, OnChanges, AfterViewChecked {
    private athenaService = inject(AthenaService);
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);
    private cdr = inject(ChangeDetectorRef);

    @Input() exercise: Exercise;
    @Input() dueDate?: dayjs.Dayjs;
    @Input() readOnly = false;

    protected readonly ExerciseType = ExerciseType;
    protected readonly AssessmentType = AssessmentType;

    readonly assessmentType: AssessmentType;

    isAthenaEnabled: boolean;
    modulesAvailable: boolean;
    availableAthenaModules: string[];
    initialAthenaModule?: string;
    showDropdownList = false;

    ngOnInit(): void {
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.athenaService.getAvailableModules(courseId, this.exercise, AthenaModuleMode.FEEDBACK_SUGGESTIONS).subscribe((modules) => {
            this.availableAthenaModules = modules;
            this.modulesAvailable = modules.length > 0;
            this.cdr.detectChanges();
        });
        this.isAthenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
        this.initialAthenaModule = this.getFeedbackModule();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.dueDate && !changes.dueDate.isFirstChange()) {
            if (this.inputControlsDisabled()) {
                this.setFeedbackModule(this.initialAthenaModule);
            }
        }
    }

    ngAfterViewChecked() {
        if (this.inputControlsDisabled()) {
            this.showDropdownList = false;
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
        this.showDropdownList = event.target.checked;
        if (event.target.checked) {
            this.setFeedbackModule(this.availableAthenaModules.first());
            this.exercise.allowManualFeedbackRequests = false;
        } else {
            this.setFeedbackModule(undefined);
        }
    }

    private hasDueDatePassed() {
        return dayjs(this.exercise.dueDate).isBefore(dayjs());
    }

    get feedbackModule(): string | undefined {
        return this.getFeedbackModule();
    }

    set feedbackModule(value: string | undefined) {
        this.setFeedbackModule(value);
    }

    get hasFeedbackModule(): boolean {
        return !!this.getFeedbackModule();
    }

    private getFeedbackModule(): string | undefined {
        return this.exercise.athenaConfig?.feedbackSuggestionModule ?? undefined;
    }

    private setFeedbackModule(value: string | undefined) {
        if (!value) {
            if (this.exercise.athenaConfig) {
                delete this.exercise.athenaConfig.feedbackSuggestionModule;
                if (!this.exercise.athenaConfig.preliminaryFeedbackModule) {
                    this.exercise.athenaConfig = undefined;
                }
            }
            return;
        }
        if (!this.exercise.athenaConfig) {
            this.exercise.athenaConfig = {};
        }
        this.exercise.athenaConfig.feedbackSuggestionModule = value;
    }
}
