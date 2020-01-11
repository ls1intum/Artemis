import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { StudentParticipation } from 'app/entities/participation';

@Component({
    selector: 'jhi-submission-result-status',
    templateUrl: './submission-result-status.component.html',
})
export class SubmissionResultStatusComponent {
    readonly ExerciseType = ExerciseType;

    @Input() exercise: Exercise;
    @Input() studentParticipation: StudentParticipation | null;
    @Input() updatingResultClass: string;
    @Input() showGradedBadge = true;
    @Input() short = false;
}
