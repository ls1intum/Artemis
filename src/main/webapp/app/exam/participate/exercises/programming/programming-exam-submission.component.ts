import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-submission-exam',
    templateUrl: './programming-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExamSubmissionComponent }],
    styleUrls: ['./programming-exam-submission.component.scss'],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent implements OnInit, OnChanges {
    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to change
    @Input()
    studentParticipation: ProgrammingExerciseStudentParticipation;

    @Input()
    exercise: ProgrammingExercise;

    isSaving: boolean;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    hasUnsavedChanges(): boolean {
        if (this.isOfflineMode()) {
            return false;
        }
        return false;
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (!this.isOfflineMode()) {
            // show submission answers in UI
        }
    }

    ngOnInit(): void {
        console.log('ExerciseId:' + this.exercise.id);
        console.log('ExerciseGroupId:' + this.exercise.exerciseGroup?.id);
        console.log('ExerciseGroupId:' + this.exercise.exerciseGroup?.exam?.id);
    }

    isOfflineMode(): boolean {
        return this.exercise.allowOfflineIde && !this.exercise.allowOnlineEditor;
    }

    updateSubmissionFromView(): void {}
}
