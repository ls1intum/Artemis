import { Component, Input, OnInit, ViewChild } from '@angular/core';
import * as moment from 'moment';
import { ButtonSize } from 'app/shared/components/button.component';
import { CodeEditorSessionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-session.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CommitState, DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import {CodeEditorContainerComponent} from "app/exercises/programming/shared/code-editor/container/code-editor-container.component";

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
export class ExamCodeEditorStudentContainerComponent implements OnInit {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;

    @Input()
    exercise: ProgrammingExercise;

    @Input()
    participation: StudentParticipation;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    repositoryIsLocked = false;
    showEditorInstructions = true;

    constructor(private domainService: DomainService) {}

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

    onFileChanged() {
        if (this.participation.submissions && this.participation.submissions.length > 0) {
            this.participation.submissions[0].isSynced = false;
        }
    }
}
