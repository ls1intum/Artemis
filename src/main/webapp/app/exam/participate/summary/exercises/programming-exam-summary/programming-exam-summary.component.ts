import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-programming-exam-summary',
    templateUrl: './programming-exam-summary.component.html',
})
export class ProgrammingExamSummaryComponent {
    @Input() exercise: ProgrammingExercise;

    @Input() participation: ProgrammingExerciseStudentParticipation;

    @Input() submission: ProgrammingSubmission;

    @Input() isTestRun?: boolean = false;

    @Input() exam: Exam;

    @Input() isAfterStudentReviewStart?: boolean = false;

    protected readonly AssessmentType = AssessmentType;
    protected readonly ProgrammingExercise = ProgrammingExercise;
}
