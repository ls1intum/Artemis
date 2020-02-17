import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { AgentParticipation } from 'app/entities/participation';

@Component({
    selector: 'jhi-submission-result-status',
    templateUrl: './submission-result-status.component.html',
})
export class SubmissionResultStatusComponent {
    readonly ExerciseType = ExerciseType;

    /**
     * @property exercise Exercise to which the submission's participation belongs
     * @property agentParticipation Participation to which the submission belongs (optional, used for updating-result)
     * @property updatingResultClass Class(es) that will be applied to the updating-result component
     * @property showGradedBadge Flag whether a colored badge (saying e.g. "Graded") should be shown
     * @property short Flag whether the short version of the result text should be used
     */
    @Input() exercise: Exercise;
    @Input() agentParticipation: AgentParticipation | null;
    @Input() updatingResultClass: string;
    @Input() showGradedBadge = false;
    @Input() short = false;

    /**
     * If a student participation is supplied explicitly (even if null), use that one.
     * Otherwise, use the first student participation on the exercise.
     */
    get participation() {
        if (this.agentParticipation !== undefined) {
            return this.agentParticipation;
        }
        return this.exercise.agentParticipations[0];
    }
}
