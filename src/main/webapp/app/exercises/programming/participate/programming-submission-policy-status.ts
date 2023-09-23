import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';

@Component({
    selector: 'jhi-programming-submission-policy-status',
    template: `
        <div submissionPolicy *ngIf="exercise.submissionPolicy && exercise.submissionPolicy.active && submissionCount !== undefined">
            <span>
                {{
                    'artemisApp.programmingExercise.submissionPolicy.submissionsAllowed'
                        | artemisTranslate: { submissionCount: submissionCount, totalSubmissions: exercise.submissionPolicy.submissionLimit }
                }}
            </span>
            <span *ngIf="exercise.submissionPolicy.type === SubmissionPolicyType.SUBMISSION_PENALTY">
                {{
                    'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInfoLabel' | artemisTranslate: { points: exercise.submissionPolicy.exceedingPenalty }
                }}
            </span>
        </div>
    `,
})
export class ProgrammingSubmissionPolicyStatusComponent {
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    submissionCount?: number;
    readonly SubmissionPolicyType = SubmissionPolicyType;
}
