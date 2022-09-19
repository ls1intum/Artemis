import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-submission-result-status',
    templateUrl: './submission-result-status.component.html',
})
export class SubmissionResultStatusComponent {
    readonly ExerciseType = ExerciseType;

    /**
     * @property exercise Exercise to which the submission's participation belongs
     * @property studentParticipation Participation to which the submission belongs (optional, used for updating-result)
     * @property updatingResultClass Class(es) that will be applied to the updating-result component
     * @property showGradedBadge Flag whether a colored badge (saying e.g. "Graded") should be shown
     * @property showUngradedResults Flag whether ungraded results should also be shown
     * @property short Flag whether the short version of the result text should be used
     */
    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() updatingResultClass: string;
    @Input() showGradedBadge = false;
    @Input() showUngradedResults: boolean;
    @Input() short = false;
    @Input() triggerLastGraded = true;

    /**
     * If a student participation is supplied explicitly, use that one.
     * Otherwise, use the first student participation on the exercise.
     */
    get participation() {
        return this.studentParticipation ?? this.exercise.studentParticipations?.first();
    }
}
