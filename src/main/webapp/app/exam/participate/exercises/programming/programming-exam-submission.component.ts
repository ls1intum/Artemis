import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExamCodeEditorStudentContainerComponent } from 'app/exam/participate/exercises/programming/code-editor/exam-code-editor-student-container.component';
import { EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

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
    @Input()
    courseId: number;

    @ViewChild(ExamCodeEditorStudentContainerComponent, { static: false })
    codeEditorComponent: ExamCodeEditorStudentContainerComponent;

    isSaving: boolean;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    hasUnsavedChanges(): boolean {
        if (this.isNotOfflineIdeMode()) {
            return false;
        }
        return this.codeEditorComponent.editorState === EditorState.UNSAVED_CHANGES;
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    ngOnChanges(changes: SimpleChanges): void {
        if (!this.isNotOfflineIdeMode()) {
            // show submission answers in UI
        }
    }

    ngOnInit(): void {}

    isNotOfflineIdeMode(): boolean {
        return this.exercise.allowOfflineIde && !this.exercise.allowOnlineEditor;
    }

    isOnlyCodeEditorMode(): boolean {
        return !this.exercise.allowOfflineIde && this.exercise.allowOnlineEditor;
    }

    updateSubmissionFromView(): void {
        // Saves the changed files but does not commit the changes, as this would trigger a CI run
        this.codeEditorComponent.actions.commit();
    }
}
