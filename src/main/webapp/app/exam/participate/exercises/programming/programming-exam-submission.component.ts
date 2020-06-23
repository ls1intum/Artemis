import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExamCodeEditorStudentContainerComponent } from 'app/exam/participate/exercises/programming/code-editor/exam-code-editor-student-container.component';
import { EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-programming-submission-exam',
    templateUrl: './programming-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExamSubmissionComponent }],
    styleUrls: ['./programming-exam-submission.component.scss'],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    @ViewChild(ExamCodeEditorStudentContainerComponent, { static: false })
    codeEditorComponent: ExamCodeEditorStudentContainerComponent;

    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input()
    exercise: ProgrammingExercise;

    hasUnsavedChanges(): boolean {
        return this.codeEditorComponent.editorState == EditorState.UNSAVED_CHANGES;
    }

    ngOnInit(): void {}

    updateSubmissionFromView(): void {
        this.codeEditorComponent.actions.commit();
    }
}
