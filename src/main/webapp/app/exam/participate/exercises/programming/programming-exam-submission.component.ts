import { Exercise } from 'app/entities/exercise.model';
import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CommitState, EditorState, FileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { AnnotationArray } from 'app/entities/annotation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-submission-exam',
    templateUrl: './programming-exam-submission.component.html',
    providers: [ProgrammingExamSubmissionComponent],
    styleUrls: ['./programming-exam-submission.component.scss'],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent implements OnInit, OnChanges {
    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to changes
    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;
    @ViewChild(CodeEditorAceComponent, { static: false }) aceEditor: CodeEditorAceComponent;
    @ViewChild(CodeEditorGridComponent, { static: false }) grid: CodeEditorGridComponent;

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

    applyMixins(derivedCtor: any, baseCtors: any[]) {
        baseCtors.forEach((baseCtor) => {
            Object.getOwnPropertyNames(baseCtor.prototype).forEach((name) => {
                if (name !== 'constructor') {
                    derivedCtor.prototype[name] = baseCtor.prototype[name];
                }
            });
        });
    }

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

    /**
    //Below code is from code-editor-mode-container
    get unsavedFiles() {
        return this.unsavedFilesValue;
    }


     * Setting unsaved files also updates the editorState / commitState.
     * - unsaved files empty -> EditorState.CLEAN
     * - unsaved files NOT empty -> EditorState.UNSAVED_CHANGES
     * - unsaved files empty AND editorState.SAVING -> CommitState.UNCOMMITTED_CHANGES
     * @param unsavedFiles
     *
    set unsavedFiles(unsavedFiles: { [fileName: string]: string }) {
        this.unsavedFilesValue = unsavedFiles;
        if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.SAVING) {
            this.editorState = EditorState.CLEAN;
            this.commitState = CommitState.UNCOMMITTED_CHANGES;
        } else if (_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.CLEAN;
        } else {
            this.editorState = EditorState.UNSAVED_CHANGES;
        }
    }
     */
}
