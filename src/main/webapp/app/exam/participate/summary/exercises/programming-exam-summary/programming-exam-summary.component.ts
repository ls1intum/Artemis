import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { createAdjustedRepositoryUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';

@Component({
    selector: 'jhi-programming-exam-summary',
    templateUrl: './programming-exam-summary.component.html',
})
export class ProgrammingExamSummaryComponent {
    @Input()
    exercise: ProgrammingExercise;

    @Input()
    participation: ProgrammingExerciseStudentParticipation;

    @Input()
    submission: ProgrammingSubmission;

    constructor() {}

    adjustRepositoryUrl() {
        return this.participation ? createAdjustedRepositoryUrl(this.participation) : '';
    }
}
