import { Component, Input, ViewChild } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExamCodeEditorStudentContainerComponent } from 'app/exam/participate/exercises/programming/code-editor/exam-code-editor-student-container.component';
import { EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Submission } from 'app/entities/submission.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-submission-exam',
    templateUrl: './programming-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExamSubmissionComponent }],
    styleUrls: ['./programming-exam-submission.component.scss'],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent {
    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to change
    @Input()
    studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    courseId: number;

    getSubmission(): Submission | null {
        if (this.studentParticipation && this.studentParticipation.submissions && this.studentParticipation.submissions.length > 0) {
            return this.studentParticipation.submissions[0];
        }
        return null;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    @ViewChild(ExamCodeEditorStudentContainerComponent, { static: false })
    codeEditorComponent: ExamCodeEditorStudentContainerComponent;

    isSaving: boolean;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    hasUnsavedChanges(): boolean {
        if (this.exercise.allowOfflineIde && !this.exercise.allowOnlineEditor) {
            return false;
        }
        return this.codeEditorComponent.editorState === EditorState.UNSAVED_CHANGES;
    }

    onActivate(): void {
        if (this.codeEditorComponent) {
            this.codeEditorComponent.reload();
        }
    }

    updateSubmissionFromView(): void {
        // Note: we just save here and do not commit, because this can lead to problems!
        this.codeEditorComponent.actions.onSave();
    }
}
