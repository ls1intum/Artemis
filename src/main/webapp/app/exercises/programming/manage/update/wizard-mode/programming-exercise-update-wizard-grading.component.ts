import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-grading',
    templateUrl: './programming-exercise-update-wizard-grading.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardGradingComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() staticCodeAnalysisAllowed: boolean;
    @Input() onStaticCodeAnalysisChanged: () => void;
    @Input() maxPenaltyPattern: string;

    faQuestionCircle = faQuestionCircle;

    getGradingSummary() {
        const summary = [];

        if (!this.programmingExercise.maxPoints) {
            return '';
        }

        const exerciseType = this.programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS ? 'bonus' : '';
        summary.push(`There is a total of ${this.programmingExercise.maxPoints} points to achieve in this ${exerciseType} exercise.`);

        if (this.programmingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            summary.push('There is no bonus to achieve.');
        } else if (this.programmingExercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY) {
            summary.push(`${this.programmingExercise.bonusPoints ?? 0} bonus points can be achieved.`);
        }

        if (this.programmingExercise.assessmentType) {
            const assessmentType = this.programmingExercise.assessmentType === AssessmentType.AUTOMATIC ? 'automatically' : 'semi-automatically';
            summary.push(`This exercise will be assessed ${assessmentType}.`);
        }

        if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.LOCK_REPOSITORY) {
            if (this.programmingExercise.submissionPolicy.submissionLimit) {
                summary.push(`This repositories will be locked after ${this.programmingExercise.submissionPolicy.submissionLimit} submissions.`);
            }
        } else if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.SUBMISSION_PENALTY) {
            if (this.programmingExercise.submissionPolicy.submissionLimit && this.programmingExercise.submissionPolicy.exceedingPenalty) {
                summary.push(
                    `There will be a penalty of ${this.programmingExercise.submissionPolicy.exceedingPenalty} points for every submission after ${this.programmingExercise.submissionPolicy.submissionLimit} submissions.`,
                );
            }
        } else {
            summary.push(`There is no limit for the amount of allowed submissions.`);
        }

        if (this.programmingExercise.staticCodeAnalysisEnabled) {
            const maxPenalty = (this.programmingExercise.maxPoints * (this.programmingExercise.maxStaticCodeAnalysisPenalty ?? 100)) / 100;
            summary.push(`Static code analysis is enabled for this exercise and there is a maximum penalty of ${maxPenalty} points from static code analysis.`);
        } else {
            summary.push(`Static code analysis is disabled for this exercise.`);
        }

        return summary.join(' ');
    }
}
