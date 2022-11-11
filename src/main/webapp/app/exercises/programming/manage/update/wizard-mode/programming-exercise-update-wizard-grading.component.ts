import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-grading',
    templateUrl: './programming-exercise-update-wizard-grading.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardGradingComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    private translationBasePath = 'artemisApp.programmingExercise.wizardMode.gradingLabels.';

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() staticCodeAnalysisAllowed: boolean;
    @Input() onStaticCodeAnalysisChanged: () => void;
    @Input() maxPenaltyPattern: string;

    faQuestionCircle = faQuestionCircle;

    constructor(private translateService: TranslateService) {}

    getGradingSummary() {
        const summary = [];

        if (!this.programmingExercise.maxPoints) {
            return '';
        }

        const replacements = {
            exerciseType: this.programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS ? 'bonus exercise' : 'exercise',
            maxPoints: this.programmingExercise.maxPoints.toString(),
            bonusPoints: (this.programmingExercise.bonusPoints ?? 0).toString(),
            assessmentType: this.programmingExercise.assessmentType === AssessmentType.AUTOMATIC ? 'automatically' : 'semi-automatically',
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
            replacements.hasOwnProperty(placeholderWithoutDelimiters) ? replacements[placeholderWithoutDelimiters] : placeholderWithDelimiters,
        );
    }
}
