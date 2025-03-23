import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { SubmissionPolicyType } from 'app/exercise/entities/submission-policy.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-submission-policy-status',
    template: `
        @if (exercise.submissionPolicy && exercise.submissionPolicy.active && submissionCount !== undefined) {
            <div submissionPolicy>
                <span
                    jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionsAllowed"
                    [translateValues]="{ submissionCount: submissionCount, totalSubmissions: exercise.submissionPolicy.submissionLimit }"
                ></span>
                @if (exercise.submissionPolicy.type === SubmissionPolicyType.SUBMISSION_PENALTY) {
                    <span
                        jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInfoLabel"
                        [translateValues]="{ points: exercise.submissionPolicy.exceedingPenalty }"
                    ></span>
                }
            </div>
        }
    `,
    imports: [TranslateDirective],
})
export class ProgrammingSubmissionPolicyStatusComponent {
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    submissionCount?: number;
    readonly SubmissionPolicyType = SubmissionPolicyType;
}
