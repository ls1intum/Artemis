import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

@Component({
    selector: 'jhi-programming-exam-summary',
    templateUrl: './programming-exam-summary.component.html',
    styles: [],
})
export class ProgrammingExamSummaryComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Input()
    participation: ProgrammingExerciseStudentParticipation;

    submission: ProgrammingSubmission;

    constructor() {}

    ngOnInit() {
        this.submission = this.participation.submissions[0] as ProgrammingSubmission;
    }
}
