import { Component, Input, OnInit, ViewChild } from '@angular/core';
import * as moment from 'moment';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { ButtonSize } from 'app/shared/components/button.component';
import { CodeEditorSessionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-session.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/code-editor-mode-container.component';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CommitState, DomainType, FileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Component({
    selector: 'jhi-exam-code-editor-student',
    templateUrl: './exam-code-editor-student-container.component.html',
    providers: [
        CodeEditorConflictStateService,
        CodeEditorSessionService,
        CodeEditorSubmissionService,
        CodeEditorBuildLogService,
        CodeEditorRepositoryFileService,
        CodeEditorRepositoryService,
        DomainService,
    ],
})
export class ExamCodeEditorStudentContainerComponent extends CodeEditorContainerComponent implements OnInit {
    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;
    @ViewChild(CodeEditorAceComponent, { static: false }) aceEditor: CodeEditorAceComponent;

    @Input()
    exercise: ProgrammingExercise;

    @Input()
    participation: StudentParticipation;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    repositoryIsLocked = false;

    constructor(
        private domainService: DomainService,
        translateService: TranslateService,
        jhiAlertService: AlertService,
        sessionService: CodeEditorSessionService,
        fileService: CodeEditorFileService,
    ) {
        super(null, translateService, null, jhiAlertService, sessionService, fileService);
    }

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        // We lock the repository when the buildAndTestAfterDueDate is set and the due date has passed.
        const dueDateHasPassed = !this.exercise.dueDate || moment(this.exercise.dueDate).isBefore(moment());
        this.repositoryIsLocked = !!this.exercise.buildAndTestStudentSubmissionsAfterDueDate && !!this.exercise.dueDate && dueDateHasPassed;

        const participation = { ...this.participation, exercise: this.exercise } as StudentParticipation;
        this.domainService.setDomain([DomainType.PARTICIPATION, participation]);
    }

    reload(): void {
        // this.ngOnInit();
        // if (this.instructions) {
        //     this.instructions.refreshInstructions();
        // }
    }

    /**
     * Update {@link Submission#isSynced} & {@link Submission#submitted} based on the CommitState.
     * The submission is only synced, if all changes are committed (CommitState.CLEAN).
     *
     * @param commitState current CommitState from CodeEditorActionsComponent
     */
    onCommitStateChange(commitState: CommitState): void {
        if (this.participation.submissions && this.participation.submissions.length > 0) {
            if (commitState === CommitState.CLEAN) {
                this.participation.submissions[0].submitted = true;
                this.participation.submissions[0].isSynced = true;
            }
        }
    }

    /**
     * Set Submission to unsynced on file changes
     *
     * @param $event
     */
    onFileChange<F extends FileChange>($event: [string[], F]) {
        super.onFileChange($event);
        if (this.participation.submissions && this.participation.submissions.length > 0) {
            this.participation.submissions[0].isSynced = false;
        }
    }

    /**
     * When the content of a file changes, set the submission status to unsynchronised.
     */
    onFileContentChange($event: { file: string; fileContent: string }) {
        super.onFileContentChange($event);
        if (this.participation.submissions && this.participation.submissions.length > 0) {
            this.participation.submissions[0].isSynced = false;
        }
    }
}
