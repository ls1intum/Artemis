import { Component, OnChanges, OnInit, inject, input, viewChild } from '@angular/core';
import { Submission } from 'app/entities/submission.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { CommitState, DomainType, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/entities/exercise.model';
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
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisProgrammingSubmissionPolicyStatusModule } from 'app/exercises/programming/participate/programming-submission-policy-status.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';

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
    standalone: true,
    imports: [
        TranslateDirective,
        ArtemisSharedComponentModule,
        ArtemisCodeEditorModule,
        ArtemisProgrammingSubmissionPolicyStatusModule,
        ArtemisExerciseButtonsModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
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

    ngOnChanges(): void {
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
        const participation = { ...this.studentParticipation(), exercise: this.exercise() } as StudentParticipation;
        this.domainService.setDomain([DomainType.PARTICIPATION, participation]);
    }

    /**
     * Sets the submission count and lock based on the student participation.
     */
    setSubmissionCountAndLockIfNeeded() {
        this.submissionCount = this.studentParticipation().submissionCount ?? this.submissionCount;
        this.repositoryIsLocked = this.studentParticipation().locked ?? this.repositoryIsLocked;
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
