import { Component, OnChanges, OnInit, inject, input, viewChild } from '@angular/core';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';

import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ProgrammingSubmissionPolicyStatusComponent } from 'app/programming/shared/entities/programming-submission-policy-status';
import { ExerciseDetailsStudentActionsComponent } from 'app/core/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { CommitState, DomainType, EditorState } from 'app/programming/shared/code-editor/model/code-editor.model';

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
    imports: [
        TranslateDirective,
        IncludedInScoreBadgeComponent,
        CodeEditorContainerComponent,
        ProgrammingSubmissionPolicyStatusComponent,
        ExerciseDetailsStudentActionsComponent,
        CodeEditorRepositoryIsLockedComponent,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructionComponent,
    ],
})
export class ProgrammingExamSubmissionComponent extends ExamSubmissionComponent implements OnChanges, OnInit {
    private domainService = inject(DomainService);

    exerciseType = ExerciseType.PROGRAMMING;

    codeEditorContainer = viewChild.required(CodeEditorContainerComponent);
    instructions = viewChild.required(ProgrammingExerciseInstructionComponent);

    // IMPORTANT: this reference must be activeExercise.studentParticipation[0] otherwise the parent component will not be able to react to change
    studentParticipation = input.required<ProgrammingExerciseStudentParticipation>();
    exercise = input.required<ProgrammingExercise>();
    courseId = input.required<number>();

    showEditorInstructions = true;
    hasSubmittedOnce = false;
    submissionCount?: number;
    repositoryIsLocked = false;

    readonly SubmissionPolicyType = SubmissionPolicyType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly getCourseFromExercise = getCourseFromExercise;

    getSubmission(): Submission | undefined {
        const studentParticipation = this.studentParticipation(); // Dereference the signal

        if (studentParticipation?.submissions && studentParticipation.submissions.length > 0) {
            return studentParticipation.submissions[0];
        }
        return undefined;
    }

    getExerciseId(): number | undefined {
        return this.exercise().id;
    }

    getExercise(): Exercise {
        return this.exercise();
    }

    isSaving: boolean;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        this.updateDomain();
        this.setSubmissionCountAndLockIfNeeded();
    }

    ngOnChanges() {
        this.setSubmissionCountAndLockIfNeeded();
    }

    onActivate() {
        super.onActivate();
        this.instructions().updateMarkdown();
        this.updateDomain();
    }

    /**
     * Updates the domain to set the active student participation
     */
    updateDomain() {
        const participation = { ...this.studentParticipation(), exercise: this.exercise() } satisfies StudentParticipation;
        this.domainService.setDomain([DomainType.PARTICIPATION, participation]);
    }

    /**
     * Sets the submission count and lock based on the student participation.
     */
    setSubmissionCountAndLockIfNeeded() {
        this.submissionCount = this.studentParticipation().submissionCount ?? this.submissionCount;
        // TODO: update repositoryIsLocked with the actual value from the server
    }

    /**
     * Update {@link Submission#isSynced} & {@link Submission#submitted} based on the CommitState.
     * The submission is only synced, if all changes are committed (CommitState.CLEAN).
     *
     * @param commitState current CommitState from CodeEditorActionsComponent
     */
    onCommitStateChange(commitState: CommitState): void {
        const studentParticipation = this.studentParticipation();
        if (studentParticipation?.submissions && studentParticipation.submissions.length > 0) {
            const firstSubmission = studentParticipation.submissions[0];
            if (commitState === CommitState.CLEAN && this.hasSubmittedOnce) {
                firstSubmission.submitted = true;
                firstSubmission.isSynced = true;
            } else if (commitState !== CommitState.UNDEFINED && !this.hasSubmittedOnce) {
                this.hasSubmittedOnce = true;
            }
        }
    }

    onFileChanged() {
        const studentParticipation = this.studentParticipation();
        if (studentParticipation?.submissions && studentParticipation.submissions.length > 0) {
            studentParticipation.submissions[0].isSynced = false;
        }
    }

    hasUnsavedChanges(): boolean {
        if (this.exercise().allowOfflineIde && !this.exercise().allowOnlineEditor) {
            return false;
        }
        return this.codeEditorContainer().editorState === EditorState.UNSAVED_CHANGES;
    }

    updateSubmissionFromView(): void {
        // Note: we just save here and do not commit, because this can lead to problems!
        this.codeEditorContainer().actions.onSave();
    }

    updateViewFromSubmission(): void {
        // do nothing - the code editor itself is taking care of updating the view from submission
    }
    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        // if we do not assign the parameter, eslint will complain because either the parameter is unused or if we suppress this with ts-ignore that ts-ignore shadows compilation errors.
        this.submissionVersion = submissionVersion;
        // submission versions are not supported for programming exercises
        throw new Error('Submission versions are not supported for file upload exercises.');
    }
}
