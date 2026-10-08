import { AfterViewInit, Component, EffectCleanupRegisterFn, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { Observable, Subject } from 'rxjs';
import { FormsModule, NgModel } from '@angular/forms';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { ProgrammingExerciseTimelineComponent } from '../../../../shared/programming-exercise-update-timeline/programming-exercise-timeline.component';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Message } from 'primeng/message';
import { TimelineStatus } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-programming-exercise-grading',
    templateUrl: './programming-exercise-grading.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        IncludedInOverallScorePickerComponent,
        FormsModule,
        FaIconComponent,
        NgbTooltip,
        SubmissionPolicyUpdateComponent,
        ProgrammingExerciseTimelineComponent,
        GradingInstructionsDetailsComponent,
        PresentationScoreComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
        Message,
    ],
})
export class ProgrammingExerciseGradingComponent implements AfterViewInit {
    private translateService = inject(TranslateService);

    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly AssessmentType = AssessmentType;
    protected readonly faQuestionCircle = faQuestionCircle;

    private translationBasePath = 'artemisApp.programmingExercise.wizardMode.gradingLabels.';

    programmingExercise = input.required<ProgrammingExercise>();
    programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    importOptions = input.required<ImportOptions>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();
    exercisePartOfExerciseGroup = input<boolean>(false);
    editGroupDates = output<void>();
    editable = input(true);
    criteriaGenerated = output<void>();

    submissionPolicyUpdateComponent = viewChild(SubmissionPolicyUpdateComponent);
    gradingInstructionsDetails = viewChild(GradingInstructionsDetailsComponent);
    maxScoreField = viewChild<NgModel>('maxScore');
    bonusPointsField = viewChild<NgModel>('bonusPoints');
    maxPenaltyField = viewChild<NgModel>('maxPenalty');

    formValidSignal = signal<boolean>(false);
    timelineStatus = signal<TimelineStatus>({ valid: true, empty: false, invalidItems: [] });

    formValid!: boolean; // assigned in calculateFormStatus(); left unset so parent's `?? false` / `=== false` reads can distinguish "not yet computed"
    formEmpty!: boolean; // assigned in calculateFormStatus() (see formValid)
    formValidChanges = new Subject<boolean>();

    readonly editPolicyUrl = signal<string | undefined>(undefined);

    constructor() {
        // A field only exists while its part of the form is shown: the max penalty once static code analysis is on, the policy
        // with its edit field. That can begin after the first render, and a subscription made once after that render would
        // never see such a field, leaving the form status stale. Each subscription therefore follows its field.
        effect((onCleanup) => this.recalculateOnChangeOf(this.maxScoreField()?.valueChanges, onCleanup));
        effect((onCleanup) => this.recalculateOnChangeOf(this.bonusPointsField()?.valueChanges, onCleanup));
        effect((onCleanup) => this.recalculateOnChangeOf(this.maxPenaltyField()?.valueChanges, onCleanup));
        effect((onCleanup) => this.recalculateOnChangeOf(this.submissionPolicyUpdateComponent()?.form?.valueChanges, onCleanup));

        // A field that appears or disappears changes what counts for the validity as well, for example an invalid max penalty stops
        // counting when static code analysis is switched off. Nothing is emitted for that, so the status is recalculated here.
        // The first run only sees the fields before the first render, which the timeline status event already covers.
        let firstRun = true;
        effect(() => {
            this.maxScoreField();
            this.bonusPointsField();
            this.maxPenaltyField();
            this.submissionPolicyUpdateComponent();
            if (firstRun) {
                firstRun = false;
                return;
            }
            untracked(() => this.calculateFormStatus());
        });
    }

    ngAfterViewInit() {
        this.setEditPolicyPageLink();
    }

    private recalculateOnChangeOf(changes: Observable<unknown> | null | undefined, onCleanup: EffectCleanupRegisterFn) {
        const subscription = changes?.subscribe(() => this.calculateFormStatus());
        onCleanup(() => subscription?.unsubscribe());
    }

    /**
     * Flushes pending text-mode grading-instruction markdown before the host save disables this form.
     *
     * @returns false when that text was rejected and the host must abort the save.
     */
    prepareForSave(): boolean {
        return this.gradingInstructionsDetails()?.prepareForSave() !== false;
    }

    calculateFormStatus() {
        const programmingExercise = this.programmingExercise();
        const maxScoreMissingAndOptional =
            programmingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED &&
            (programmingExercise.maxPoints === undefined || programmingExercise.maxPoints === null);
        const maxScoreValidOrOptional = this.maxScoreField()?.valid || maxScoreMissingAndOptional;
        // Bonus points are only entered (and the field only rendered) when the exercise is INCLUDED_COMPLETELY,
        // so its validity must not block the form in the other modes (the field is hidden via [hidden]).
        const bonusPointsValidOrHidden = this.bonusPointsField()?.valid || programmingExercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY;
        const maxPenaltyValidOrDisabled = this.maxPenaltyField()?.valid || !programmingExercise.staticCodeAnalysisEnabled;
        const scoreFieldsValid = maxScoreValidOrOptional && bonusPointsValidOrHidden && maxPenaltyValidOrDisabled;
        const timelineStatus = this.timelineStatus();
        const dependentComponentsValid = !this.submissionPolicyUpdateComponent()?.invalid && timelineStatus.valid;
        const newFormValidValue = Boolean(scoreFieldsValid && dependentComponentsValid);

        this.formValidSignal.set(newFormValidValue);
        this.formValid = newFormValidValue;
        this.formEmpty = timelineStatus.empty;
        this.formValidChanges.next(this.formValid);
    }

    onTimelineStatusChange(timelineStatus: TimelineStatus): void {
        this.timelineStatus.set(timelineStatus);
        this.calculateFormStatus();
    }

    onIncludedInOverallScoreChange(includedInOverallScore: IncludedInOverallScore): void {
        const programmingExercise = this.programmingExercise();
        programmingExercise.includedInOverallScore = includedInOverallScore;
        if (includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            programmingExercise.maxPoints = 0;
        } else if (!programmingExercise.maxPoints) {
            programmingExercise.maxPoints = 1;
        }
        if (includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY) {
            programmingExercise.bonusPoints = 0;
        }
        this.calculateFormStatus();
    }

    getGradingSummary() {
        const summary = [];

        const programmingExercise = this.programmingExercise();
        if (!programmingExercise.maxPoints) {
            return '';
        }

        const exerciseType = programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS ? 'bonusExercise' : 'normalExercise';
        const assessmentType = programmingExercise.assessmentType === AssessmentType.AUTOMATIC ? 'assessmentAutomatic' : 'assessmentSemiautomatic';
        const replacements = {
            exerciseType: this.translateService.instant(this.translationBasePath + exerciseType),
            maxPoints: programmingExercise.maxPoints.toString(),
            bonusPoints: (programmingExercise.bonusPoints ?? 0).toString(),
            assessmentType: this.translateService.instant(this.translationBasePath + assessmentType),
            submissionLimit: programmingExercise.submissionPolicy?.submissionLimit,
            exceedingPenalty: programmingExercise.submissionPolicy?.exceedingPenalty,
            maxPenalty: ((programmingExercise.maxPoints * (programmingExercise.maxStaticCodeAnalysisPenalty ?? 100)) / 100).toString(),
        };

        summary.push(this.translateService.instant(this.translationBasePath + 'points'));

        if (programmingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            summary.push(this.translateService.instant(this.translationBasePath + 'noBonus'));
        } else if (programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY) {
            summary.push(this.translateService.instant(this.translationBasePath + 'bonus'));
        }

        if (programmingExercise.assessmentType) {
            summary.push(this.translateService.instant(this.translationBasePath + 'assessment'));
        }

        if (programmingExercise.submissionPolicy?.type === SubmissionPolicyType.LOCK_REPOSITORY) {
            if (programmingExercise.submissionPolicy.submissionLimit) {
                summary.push(this.translateService.instant(this.translationBasePath + 'lockedSubmission'));
            }
        } else if (programmingExercise.submissionPolicy?.type === SubmissionPolicyType.SUBMISSION_PENALTY) {
            if (programmingExercise.submissionPolicy.submissionLimit && programmingExercise.submissionPolicy.exceedingPenalty) {
                summary.push(this.translateService.instant(this.translationBasePath + 'penaltySubmission'));
            }
        } else {
            summary.push(this.translateService.instant(this.translationBasePath + 'unrestrictedSubmission'));
        }

        if (programmingExercise.staticCodeAnalysisEnabled) {
            summary.push(this.translateService.instant(this.translationBasePath + 'staticAnalysisEnabled'));
        } else {
            summary.push(this.translateService.instant(this.translationBasePath + 'staticAnalysisDisabled'));
        }

        return summary.map((s) => this.replacePlaceholders(s, replacements)).join(' ');
    }

    replacePlaceholders(stringWithPlaceholders: string, replacements: Record<string, string | number | undefined>) {
        return stringWithPlaceholders.replace(/{(\w+)}/g, (placeholderWithDelimiters, placeholderWithoutDelimiters) =>
            this.replacePlaceholder(placeholderWithDelimiters, placeholderWithoutDelimiters, replacements),
        );
    }

    replacePlaceholder(placeholderWithDelimiters: string, placeholderWithoutDelimiters: string, replacements: Record<string, string | number | undefined>) {
        return Object.prototype.hasOwnProperty.call(replacements, placeholderWithoutDelimiters) ? String(replacements[placeholderWithoutDelimiters]) : placeholderWithDelimiters;
    }

    private setEditPolicyPageLink(): void {
        const programmingExercise = this.programmingExercise();
        const linkParts = [
            'course-management',
            getCourseFromExercise(programmingExercise)?.id,
            ...(programmingExercise?.exerciseGroup?.exam ? ['exams', programmingExercise.exerciseGroup.exam.id] : []),
            'programming-exercises',
            programmingExercise.id,
            'grading',
            'submission-policy',
        ];
        this.editPolicyUrl.set(linkParts.join('/'));
    }
} /* istanbul ignore next */
