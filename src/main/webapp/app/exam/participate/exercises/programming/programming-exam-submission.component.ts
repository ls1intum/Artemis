import { ChangeDetectorRef, Component, Input, OnChanges, OnInit, ViewChild } from '@angular/core';
import { Submission } from 'app/entities/submission.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CommitState, DomainType, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Exercise, IncludedInOverallScore, getCourseFromExercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Component({
    selector: 'jhi-programming-submission-exam',
    templateUrl: './programming-exam-submission.component.html',
    providers: [
        { provide: ExamSubmissionComponent, useExisting: ProgrammingExamSubmissionComponent },
        CodeEditorConflictStateService,
        CodeEditorSubmissionService,
        CodeEditorBuildLogService,
        CodeEditorRepositoryFileService,
        CodeEditorRepositoryService,
    ],
    styleUrls: ['./programming-exam-submission.component.scss'],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent implements OnChanges, OnInit {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    @ViewChild(ProgrammingExerciseInstructionComponent, { static: false }) instructions: ProgrammingExerciseInstructionComponent;

    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to change
    @Input()
    studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    courseId: number;

    showEditorInstructions = true;
    hasSubmittedOnce = false;
    submissionCount?: number;
    repositoryIsLocked = false;

    readonly SubmissionPolicyType = SubmissionPolicyType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly getCourseFromExercise = getCourseFromExercise;

    getSubmission(): Submission | undefined {
        if (this.studentParticipation?.submissions?.length) {
            return this.studentParticipation.submissions[0];
        }
        return undefined;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    isSaving: boolean;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    constructor(
        private domainService: DomainService,
        changeDetectorReference: ChangeDetectorRef,
    ) {
        super(changeDetectorReference);
    }

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        this.updateDomain();
        this.setSubmissionCountAndLockIfNeeded();
    }

    ngOnChanges(): void {
        this.setSubmissionCountAndLockIfNeeded();
    }

    onActivate() {
        super.onActivate();
        this.instructions.updateMarkdown();
        this.updateDomain();
    }

    /**
     * Updates the domain to set the active student participation
     */
    updateDomain() {
        const participation = { ...this.studentParticipation, exercise: this.exercise } as StudentParticipation;
        this.domainService.setDomain([DomainType.PARTICIPATION, participation]);
    }

    /**
     * Sets the submission count and lock based on the student participation.
     */
    setSubmissionCountAndLockIfNeeded() {
        this.submissionCount = this.studentParticipation.submissionCount ?? this.submissionCount;
        this.repositoryIsLocked = this.studentParticipation.locked ?? this.repositoryIsLocked;
    }

    /**
     * Update {@link Submission#isSynced} & {@link Submission#submitted} based on the CommitState.
     * The submission is only synced, if all changes are committed (CommitState.CLEAN).
     *
     * @param commitState current CommitState from CodeEditorActionsComponent
     */
    onCommitStateChange(commitState: CommitState): void {
        if (this.studentParticipation.submissions && this.studentParticipation.submissions.length > 0) {
            if (commitState === CommitState.CLEAN && this.hasSubmittedOnce) {
                this.studentParticipation.submissions[0].submitted = true;
                this.studentParticipation.submissions[0].isSynced = true;
            } else if (commitState !== CommitState.UNDEFINED && !this.hasSubmittedOnce) {
                this.hasSubmittedOnce = true;
            }
        }
    }

    onFileChanged() {
        if (this.studentParticipation.submissions && this.studentParticipation.submissions.length > 0) {
            this.studentParticipation.submissions[0].isSynced = false;
        }
    }

    hasUnsavedChanges(): boolean {
        if (this.exercise.allowOfflineIde && !this.exercise.allowOnlineEditor) {
            return false;
        }
        return this.codeEditorContainer.editorState === EditorState.UNSAVED_CHANGES;
    }

    updateSubmissionFromView(): void {
        // Note: we just save here and do not commit, because this can lead to problems!
        this.codeEditorContainer.actions.onSave();
    }

    updateViewFromSubmission(): void {
        // do nothing - the code editor itself is taking care of updating the view from submission
    }
}
