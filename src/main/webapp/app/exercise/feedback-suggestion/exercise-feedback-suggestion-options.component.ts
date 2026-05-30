import { Component, OnInit, effect, inject, input, signal } from '@angular/core';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgStyle } from '@angular/common';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-exercise-feedback-suggestion-options',
    templateUrl: './exercise-feedback-suggestion-options.component.html',
    imports: [TranslateDirective, NgStyle, HelpIconComponent, FormsModule],
})
export class ExerciseFeedbackSuggestionOptionsComponent implements OnInit {
    private readonly athenaService = inject(AthenaService);
    private readonly profileService = inject(ProfileService);
    private readonly activatedRoute = inject(ActivatedRoute);

    exercise = input.required<Exercise>();
    dueDate = input<dayjs.Dayjs | undefined>(undefined);
    readOnly = input<boolean>(false);

    protected readonly ExerciseType = ExerciseType;
    protected readonly AssessmentType = AssessmentType;

    isAthenaEnabled = signal(false);
    modulesAvailable = signal(false);
    availableAthenaModules = signal<string[]>([]);
    private initialAthenaModule: string | undefined;

    constructor() {
        effect(() => {
            const dueDate = this.dueDate();
            if (dueDate !== undefined && this.inputControlsDisabled()) {
                this.exercise().feedbackSuggestionModule = this.initialAthenaModule;
            }
        });
    }

    ngOnInit(): void {
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.athenaService.getAvailableModules(courseId, this.exercise()).subscribe((modules) => {
            this.availableAthenaModules.set(modules);
            this.modulesAvailable.set(modules.length > 0);
        });
        this.isAthenaEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA));
        this.initialAthenaModule = this.exercise().feedbackSuggestionModule;
    }

    inputControlsDisabled(): boolean {
        if (this.exercise().type === ExerciseType.PROGRAMMING) {
            return this.exercise().assessmentType === AssessmentType.AUTOMATIC || this.readOnly() || this.exercise().dueDate === undefined || this.hasDueDatePassed();
        }
        return this.hasDueDatePassed();
    }

    getCheckboxLabelStyle(): Record<string, string> {
        if (this.inputControlsDisabled()) {
            return { color: 'grey' };
        }
        return {};
    }

    toggleFeedbackSuggestions(event: Event): void {
        const checked = (event.target as HTMLInputElement).checked;
        if (checked) {
            this.exercise().feedbackSuggestionModule = this.availableAthenaModules().first();
        } else {
            this.exercise().allowFeedbackRequests = false;
            this.exercise().feedbackSuggestionModule = undefined;
        }
    }

    toggleFeedbackRequests(event: Event): void {
        const checked = (event.target as HTMLInputElement).checked;
        if (checked) {
            this.exercise().feedbackSuggestionModule = this.availableAthenaModules().first();
            this.exercise().allowFeedbackRequests = true;
        } else {
            this.exercise().allowFeedbackRequests = false;
        }
    }

    setFeedbackModule(value: string | undefined): void {
        this.exercise().feedbackSuggestionModule = value;
    }

    private hasDueDatePassed(): boolean {
        return dayjs(this.exercise().dueDate).isBefore(dayjs());
    }
}
