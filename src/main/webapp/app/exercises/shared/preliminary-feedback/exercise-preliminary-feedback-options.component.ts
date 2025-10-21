import { ChangeDetectorRef, Component, OnChanges, OnInit, SimpleChanges, inject, input } from '@angular/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { AsyncPipe, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { AthenaModuleMode } from 'app/assessment/shared/entities/athena.model';

@Component({
    selector: 'jhi-exercise-preliminary-feedback-options',
    imports: [TranslateDirective, NgStyle, HelpIconComponent, FormsModule, AsyncPipe],
    templateUrl: './exercise-preliminary-feedback-options.component.html',
})
export class ExercisePreliminaryFeedbackOptionsComponent implements OnInit, OnChanges {
    exercise = input.required<Exercise>();
    dueDate = input<dayjs.Dayjs>();
    readOnly = input<boolean>();

    protected readonly ExerciseType = ExerciseType;

    protected readonly AssessmentType = AssessmentType;

    readonly assessmentType: AssessmentType;

    isAthenaEnabled: boolean;
    modulesAvailable: boolean;
    availableAthenaModules: string[];
    initialAthenaModule?: string;
    showDropdownList: boolean = false;

    private readonly athenaService = inject(AthenaService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly cdr = inject(ChangeDetectorRef);

    ngOnInit(): void {
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.athenaService.getAvailableModules(courseId, this.exercise(), AthenaModuleMode.PRELIMINARY_FEEDBACK).subscribe((modules) => {
            this.availableAthenaModules = modules;
            this.modulesAvailable = modules.length > 0;
            this.cdr.detectChanges();
        });
        this.isAthenaEnabled = this.athenaService.isEnabled();
        this.initialAthenaModule = this.getPreliminaryModule();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.dueDate && !changes.dueDate.isFirstChange()) {
            if (this.inputControlsDisabled()) {
                this.setPreliminaryModule(this.initialAthenaModule);
            }
        }
    }

    /**
     * Returns true in case the input controls should be disabled. This is the case for all exercises when the due date has passed.
     */
    inputControlsDisabled() {
        return this.readOnly() || this.hasDueDatePassed();
    }

    /**
     * Returns the label style for the checkbox to enable preliminary feedback. In case the input controls are disabled, the label text color is set to grey.
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
            this.setPreliminaryModule(this.availableAthenaModules.first());
        } else {
            this.setPreliminaryModule(undefined);
        }
    }

    private hasDueDatePassed() {
        return dayjs(this.exercise().dueDate).isBefore(dayjs());
    }

    get preliminaryModule(): string | undefined {
        return this.getPreliminaryModule();
    }

    set preliminaryModule(value: string | undefined) {
        this.setPreliminaryModule(value);
    }

    get hasPreliminaryModule(): boolean {
        return !!this.getPreliminaryModule();
    }

    private getPreliminaryModule(): string | undefined {
        return this.exercise().athenaConfig?.preliminaryFeedbackModule ?? undefined;
    }

    private setPreliminaryModule(value: string | undefined) {
        const exercise = this.exercise();
        if (!value) {
            if (exercise.athenaConfig) {
                delete exercise.athenaConfig.preliminaryFeedbackModule;
                if (!exercise.athenaConfig.feedbackSuggestionModule) {
                    exercise.athenaConfig = undefined;
                }
            }
            return;
        }
        if (!exercise.athenaConfig) {
            exercise.athenaConfig = {};
        }
        exercise.athenaConfig.preliminaryFeedbackModule = value;
    }
}
