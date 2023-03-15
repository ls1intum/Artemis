import { Component, Input } from '@angular/core';

import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

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
}
