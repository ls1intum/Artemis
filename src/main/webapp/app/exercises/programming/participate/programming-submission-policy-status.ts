import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';

@Component({
    selector: 'jhi-programming-submission-policy-status',
    template: `
        @if (exercise.submissionPolicy && exercise.submissionPolicy.active && submissionCount !== undefined) {
            <div submissionPolicy>
                <span>
                    {{
                        'artemisApp.programmingExercise.submissionPolicy.submissionsAllowed'
                            | artemisTranslate: { submissionCount: submissionCount, totalSubmissions: exercise.submissionPolicy.submissionLimit }
                    }}
                </span>
                @if (exercise.submissionPolicy.type === SubmissionPolicyType.SUBMISSION_PENALTY) {
                    <span>
                        {{
                            'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInfoLabel'
                                | artemisTranslate: { points: exercise.submissionPolicy.exceedingPenalty }
                        }}
                    </span>
                }
            </div>
        }
    `,
})
export class ProgrammingSubmissionPolicyStatusComponent {
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    submissionCount?: number;
    readonly SubmissionPolicyType = SubmissionPolicyType;
}
