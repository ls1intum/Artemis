import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { CommitState, EditorState, FileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { AnnotationArray } from 'app/entities/annotation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExamCodeEditorStudentContainerComponent } from 'app/exam/participate/exercises/programming/code-editor/exam-code-editor-student-container.component';

@Component({
    selector: 'jhi-programming-submission-exam',
    templateUrl: './programming-exam-submission.component.html',
    providers: [ProgrammingExamSubmissionComponent],
    styleUrls: ['./programming-exam-submission.component.scss'],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent implements OnInit, OnChanges {
    @ViewChild(ExamCodeEditorStudentContainerComponent, { static: false })
    codeEditorComponent: ExamCodeEditorStudentContainerComponent;

    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input()
    exercise: ProgrammingExercise;

    isSaving: boolean;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    // WARNING: Don't initialize variables in the declaration block. The method initializeProperties is responsible for this task.
    selectedFile?: string;
    unsavedFilesValue: { [fileName: string]: string } = {}; // {[fileName]: fileContent}
    // This variable is used to inform components that a file has changed its filename, e.g. because of renaming
    fileChange?: FileChange;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

    /** Code Editor State Variables **/
    editorState: EditorState;
    commitState: CommitState;

    hasUnsavedChanges(): boolean {
        return false;
    }

    ngOnChanges(changes: SimpleChanges): void {
        // show submission answers in UI
    }

    ngOnInit(): void {
        //this.initializeProperties();
    }

    updateSubmissionFromView(): void {}
}
