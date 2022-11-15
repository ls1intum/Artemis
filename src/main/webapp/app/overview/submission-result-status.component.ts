import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-submission-result-status',
    templateUrl: './submission-result-status.component.html',
})
export class SubmissionResultStatusComponent {
    readonly ExerciseType = ExerciseType;
    readonly ParticipationStatus = ParticipationStatus;

    /**
     * @property exercise Exercise to which the submission's participation belongs
     * @property studentParticipation Participation to which the submission belongs
     * @property updatingResultClass Class(es) that will be applied to the updating-result component
     * @property showBadge Flag whether a colored badge (saying e.g. "Graded") should be shown
     * @property showUngradedResults Flag whether ungraded results should also be shown
     * @property short Flag whether the short version of the result text should be used
     */
    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() updatingResultClass: string;
    @Input() showBadge = false;
    @Input() showUngradedResults = false;
    @Input() showIcon = true;
    @Input() short = false;
    @Input() triggerLastGraded = true;
}
