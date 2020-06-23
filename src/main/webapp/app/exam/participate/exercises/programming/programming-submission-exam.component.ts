import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';

@Component({
    selector: 'jhi-programming-exam',
    templateUrl: './programming-submission-exam.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingSubmissionExamComponent }],
    styleUrls: ['./programming-submission-exam.component.scss'],
})

export class ProgrammingSubmissionExamComponent extends ExamSubmissionComponent implements OnInit, OnChanges {
    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: ProgrammingSubmission;
    @Input()
    exercise: Exercise;

    isSaving: boolean;

    hasUnsavedChanges(): boolean {
        return false;
    }

    ngOnChanges(changes: SimpleChanges): void {
        // show submission answers in UI
    }

    ngOnInit(): void {
    }

    updateSubmissionFromView(): void {
    }
}
