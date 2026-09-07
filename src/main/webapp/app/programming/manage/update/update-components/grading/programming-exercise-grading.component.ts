import { AfterViewInit, Component, OnDestroy, computed, inject, input, output, signal, viewChild } from '@angular/core';
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
import { Subject, Subscription } from 'rxjs';
import { FormsModule, NgModel } from '@angular/forms';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { ProgrammingExerciseUpdateTimelineComponent } from '../../../../shared/programming-exercise-update-timeline/programming-exercise-update-timeline.component';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Message } from 'primeng/message';
import {
    DEFAULT_MAX_POINTS_DECIMAL_PLACES,
    MAX_EXERCISE_POINTS,
    MIN_EXERCISE_POINTS,
    MIN_EXERCISE_POINTS_INCLUDED_IN_SCORE,
    buildPointsPattern,
} from 'app/foundation/constants/input.constants';

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
        ProgrammingExerciseUpdateTimelineComponent,
        GradingInstructionsDetailsComponent,
        PresentationScoreComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
        Message,
    ],
})
export class ProgrammingExerciseGradingComponent implements AfterViewInit, OnDestroy {
    private translateService = inject(TranslateService);

    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly AssessmentType = AssessmentType;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly MIN_POINTS = MIN_EXERCISE_POINTS;
    protected readonly MIN_POINTS_INCLUDED_IN_SCORE = MIN_EXERCISE_POINTS_INCLUDED_IN_SCORE;
    protected readonly MAX_POINTS = MAX_EXERCISE_POINTS;
    protected readonly maxDecimalPlaces = computed(() => getCourseFromExercise(this.programmingExercise())?.accuracyOfScores ?? DEFAULT_MAX_POINTS_DECIMAL_PLACES);
    protected readonly pointsPattern = computed(() => buildPointsPattern(this.maxDecimalPlaces()));
    // Plain methods, not computed() signals: onIncludedInOverallScoreChange() mutates programmingExercise().includedInOverallScore
    // in place rather than replacing the input signal's value, so a computed() here would never see a changed dependency version
    // and could go stale across inclusion-mode changes. Called fresh from the template on every change detection run instead.
    protected pointsErrorTranslateValues() {
        return {
            min: this.programmingExercise().includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED ? MIN_EXERCISE_POINTS : MIN_EXERCISE_POINTS_INCLUDED_IN_SCORE,
            max: MAX_EXERCISE_POINTS,
            maxDecimals: this.maxDecimalPlaces(),
        };
    }

    protected bonusPointsErrorTranslateValues() {
        return {
            min: MIN_EXERCISE_POINTS,
            max: MAX_EXERCISE_POINTS,
            maxDecimals: this.maxDecimalPlaces(),
        };
    }

    private translationBasePath = 'artemisApp.programmingExercise.wizardMode.gradingLabels.';

    programmingExercise = input.required<ProgrammingExercise>();
    programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    importOptions = input.required<ImportOptions>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();
    /** Whether grading controls may currently be changed. */
    editable = input(true);
    /** Emitted when generated criteria modify the programming exercise. */
    criteriaGenerated = output<void>();
    /** When true the timeline dates are governed by the exercise's variant group (see {@link ExerciseTimelineComponent}). */
    lockedToGroup = input<boolean>(false);
    /** Emitted when the user clicks the locked timeline so the host can open the group-edit dialog. */
    lockedClick = output<void>();

    submissionPolicyUpdateComponent = viewChild(SubmissionPolicyUpdateComponent);
    lifecycleComponent = viewChild(ProgrammingExerciseUpdateTimelineComponent);
    maxScoreField = viewChild<NgModel>('maxScore');
    bonusPointsField = viewChild<NgModel>('bonusPoints');
    maxPenaltyField = viewChild<NgModel>('maxPenalty');

    formValidSignal = signal<boolean>(false);

    formValid!: boolean; // assigned in calculateFormStatus(); left unset so parent's `?? false` / `=== false` reads can distinguish "not yet computed"
    formEmpty!: boolean; // assigned in calculateFormStatus() (see formValid)
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    readonly editPolicyUrl = signal<string | undefined>(undefined);

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.maxScoreField()?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.bonusPointsField()?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.maxPenaltyField()?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.submissionPolicyUpdateComponent()?.form?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.lifecycleComponent()?.formValidChanges?.subscribe(() => this.calculateFormStatus()));
        this.setEditPolicyPageLink();
    }

    ngOnDestroy() {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
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
        const dependentComponentsValid = !this.submissionPolicyUpdateComponent()?.invalid && this.lifecycleComponent()?.formValid;
        const newFormValidValue = Boolean(scoreFieldsValid && dependentComponentsValid);

        this.formValidSignal.set(newFormValidValue);
        this.formValid = newFormValidValue;
        this.formEmpty = this.lifecycleComponent()?.formEmpty ?? false;
        this.formValidChanges.next(this.formValid);
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
