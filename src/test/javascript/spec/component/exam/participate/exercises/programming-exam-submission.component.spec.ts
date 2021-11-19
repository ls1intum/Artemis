import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import dayjs from 'dayjs';
import { MockComponent, MockProvider } from 'ng-mocks';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';

describe('ProgrammingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ProgrammingExamSubmissionComponent>;
    let component: ProgrammingExamSubmissionComponent;

    let studentParticipation: ProgrammingExerciseStudentParticipation;
    let exercise: ProgrammingExercise;
    let programmingSubmission: ProgrammingSubmission;

    let domainService: DomainService;

    beforeEach(() => {
        programmingSubmission = { commitHash: 'Hash commit', buildFailed: false, buildArtifact: false };
        studentParticipation = { submissions: [programmingSubmission] };
        exercise = new ProgrammingExercise(new Course(), new ExerciseGroup());

        return TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExamSubmissionComponent,
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(CodeEditorContainerComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(UpdatingResultComponent),
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(SubmissionResultStatusComponent),
                TranslatePipeMock,
            ],
            providers: [
                MockProvider(DomainService),
                MockProvider(CodeEditorRepositoryService),
                MockProvider(CodeEditorRepositoryFileService),
                MockProvider(CodeEditorBuildLogService),
                MockProvider(CodeEditorSubmissionService),
                MockProvider(CodeEditorConflictStateService),
                MockProvider(ProgrammingExamSubmissionComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExamSubmissionComponent);
                component = fixture.componentInstance;
                domainService = TestBed.inject(DomainService);
            });
    });
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with unlocked repository', () => {
        const domainServiceSpy = jest.spyOn(domainService, 'setDomain');
        component.exercise = exercise;

        fixture.detectChanges();

        expect(domainServiceSpy).toHaveBeenCalledTimes(1);
        expect(component.repositoryIsLocked).toBe(false);
        expect(component.getExercise()).toBe(exercise);
    });

    it('should set the repositoryIsLocked value to true', () => {
        const programmingExercise = new ProgrammingExercise(new Course(), new ExerciseGroup());
        programmingExercise.dueDate = dayjs().subtract(10, 'seconds');
        programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().subtract(60, 'seconds');

        component.exercise = programmingExercise;
        fixture.detectChanges();

        expect(component.repositoryIsLocked).toBe(true);
    });

    it('should change state on commit', () => {
        component.studentParticipation = studentParticipation;

        const undefinedCommitState = CommitState.UNDEFINED;
        component.onCommitStateChange(undefinedCommitState);
        expect(component.hasSubmittedOnce).toBe(false);

        const cleanCommitState = CommitState.CLEAN;

        component.onCommitStateChange(cleanCommitState);
        expect(component.hasSubmittedOnce).toBe(true);

        /**
         * After the first call with CommitState.CLEAN, component.hasSubmittedOnce must be now true
         */
        component.onCommitStateChange(cleanCommitState);
        if (component.studentParticipation.submissions) {
            expect(component.studentParticipation.submissions[0].submitted).toBe(true);
            expect(component.studentParticipation.submissions[0].isSynced).toBe(true);
        }
    });

    it('should desync on file change', () => {
        component.studentParticipation = studentParticipation;
        if (component.studentParticipation.submissions) {
            component.studentParticipation.submissions[0].isSynced = true;
            component.onFileChanged();

            expect(component.studentParticipation.submissions[0].isSynced).toBe(false);
        }
    });

    it('should get submission', () => {
        component.studentParticipation = studentParticipation;
        if (studentParticipation.submissions) {
            expect(component.getSubmission()).toBe(studentParticipation.submissions[0]);
        }
    });

    it('should return false if no unsaved changes', () => {
        exercise.allowOfflineIde = true;
        exercise.allowOnlineEditor = false;
        component.exercise = exercise;

        expect(component.hasUnsavedChanges()).toBe(false);
    });
});
