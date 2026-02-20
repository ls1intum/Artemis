import { AfterContentInit, Component, Input, OnDestroy, ViewChild, inject, input, signal } from '@angular/core';
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
import { ProgrammingExerciseLifecycleComponent } from 'app/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbAlert, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
        NgbAlert,
        HelpIconComponent,
        ProgrammingExerciseLifecycleComponent,
        GradingInstructionsDetailsComponent,
        PresentationScoreComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
    ],
})
export class ProgrammingExerciseGradingComponent implements AfterContentInit, OnDestroy {
    private translateService = inject(TranslateService);

    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly AssessmentType = AssessmentType;
    protected readonly faQuestionCircle = faQuestionCircle;

    private translationBasePath = 'artemisApp.programmingExercise.wizardMode.gradingLabels.';

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @Input() importOptions: ImportOptions;
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    @ViewChild(SubmissionPolicyUpdateComponent) submissionPolicyUpdateComponent?: SubmissionPolicyUpdateComponent;
    @ViewChild(ProgrammingExerciseLifecycleComponent) lifecycleComponent?: ProgrammingExerciseLifecycleComponent;
    @ViewChild('maxScore') maxScoreField?: NgModel;
    @ViewChild('bonusPoints') bonusPointsField?: NgModel;
    @ViewChild('maxPenalty') maxPenaltyField?: NgModel;

    formValidSignal = signal<boolean>(false);

    formValid: boolean;
    formEmpty: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    editPolicyUrl: string;

    ngAfterContentInit(): void {
        this.inputFieldSubscriptions.push(this.maxScoreField?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.bonusPointsField?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.maxPenaltyField?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.submissionPolicyUpdateComponent?.form?.valueChanges?.subscribe(() => this.calculateFormStatus()));
        this.inputFieldSubscriptions.push(this.lifecycleComponent?.formValidChanges?.subscribe(() => this.calculateFormStatus()));
        this.setEditPolicyPageLink();
    }

    ngOnDestroy() {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormStatus() {
        const newFormValidValue = Boolean(
            this.maxScoreField?.valid &&
            this.bonusPointsField?.valid &&
            (this.maxPenaltyField?.valid || !this.programmingExercise.staticCodeAnalysisEnabled) &&
            !this.submissionPolicyUpdateComponent?.invalid &&
            this.lifecycleComponent?.formValid,
        );

        this.formValidSignal.set(newFormValidValue);
        this.formValid = newFormValidValue;
        this.formEmpty = this.lifecycleComponent?.formEmpty ?? false;
        this.formValidChanges.next(this.formValid);
    }

    getGradingSummary() {
        const summary = [];

        if (!this.programmingExercise.maxPoints) {
            return '';
        }

        const exerciseType = this.programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS ? 'bonusExercise' : 'normalExercise';
        const assessmentType = this.programmingExercise.assessmentType === AssessmentType.AUTOMATIC ? 'assessmentAutomatic' : 'assessmentSemiautomatic';
        const replacements = {
            exerciseType: this.translateService.instant(this.translationBasePath + exerciseType),
            maxPoints: this.programmingExercise.maxPoints.toString(),
            bonusPoints: (this.programmingExercise.bonusPoints ?? 0).toString(),
            assessmentType: this.translateService.instant(this.translationBasePath + assessmentType),
            submissionLimit: this.programmingExercise.submissionPolicy?.submissionLimit,
            exceedingPenalty: this.programmingExercise.submissionPolicy?.exceedingPenalty,
            maxPenalty: ((this.programmingExercise.maxPoints * (this.programmingExercise.maxStaticCodeAnalysisPenalty ?? 100)) / 100).toString(),
        };

        summary.push(this.translateService.instant(this.translationBasePath + 'points'));

        if (this.programmingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            summary.push(this.translateService.instant(this.translationBasePath + 'noBonus'));
        } else if (this.programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY) {
            summary.push(this.translateService.instant(this.translationBasePath + 'bonus'));
        }

        if (this.programmingExercise.assessmentType) {
            summary.push(this.translateService.instant(this.translationBasePath + 'assessment'));
        }

        if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.LOCK_REPOSITORY) {
            if (this.programmingExercise.submissionPolicy.submissionLimit) {
                summary.push(this.translateService.instant(this.translationBasePath + 'lockedSubmission'));
            }
        } else if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.SUBMISSION_PENALTY) {
            if (this.programmingExercise.submissionPolicy.submissionLimit && this.programmingExercise.submissionPolicy.exceedingPenalty) {
                summary.push(this.translateService.instant(this.translationBasePath + 'penaltySubmission'));
            }
        } else {
            summary.push(this.translateService.instant(this.translationBasePath + 'unrestrictedSubmission'));
        }

        if (this.programmingExercise.staticCodeAnalysisEnabled) {
            summary.push(this.translateService.instant(this.translationBasePath + 'staticAnalysisEnabled'));
        } else {
            summary.push(this.translateService.instant(this.translationBasePath + 'staticAnalysisDisabled'));
        }

        return summary.map((s) => this.replacePlaceholders(s, replacements)).join(' ');
    }

    replacePlaceholders(stringWithPlaceholders: string, replacements: any) {
        return stringWithPlaceholders.replace(/{(\w+)}/g, (placeholderWithDelimiters, placeholderWithoutDelimiters) =>
            this.replacePlaceholder(placeholderWithDelimiters, placeholderWithoutDelimiters, replacements),
        );
    }

    replacePlaceholder(placeholderWithDelimiters: string, placeholderWithoutDelimiters: any, replacements: any) {
        return Object.prototype.hasOwnProperty.call(replacements, placeholderWithoutDelimiters) ? replacements[placeholderWithoutDelimiters] : placeholderWithDelimiters;
    }

    private setEditPolicyPageLink(): void {
        const linkParts = [
            'course-management',
            getCourseFromExercise(this.programmingExercise)?.id,
            ...(this.programmingExercise?.exerciseGroup?.exam ? ['exams', this.programmingExercise.exerciseGroup.exam.id] : []),
            'programming-exercises',
            this.programmingExercise.id,
            'grading',
            'submission-policy',
        ];
        this.editPolicyUrl = linkParts.join('/');
    }
} /* istanbul ignore next */
