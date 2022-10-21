import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { CommitState, DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';

describe('ProgrammingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ProgrammingExamSubmissionComponent>;
    let component: ProgrammingExamSubmissionComponent;

    let domainService: DomainService;
    let domainServiceSetDomainSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExamSubmissionComponent,
                MockComponent(CodeEditorContainerComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(ModelingEditorComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(UpdatingResultComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(ChangeDetectorRef), MockProvider(DomainService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExamSubmissionComponent);
                component = fixture.componentInstance;
                domainService = fixture.debugElement.injector.get(DomainService);

                domainServiceSetDomainSpy = jest.spyOn(domainService, 'setDomain');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const newExercise = () => {
        return new ProgrammingExercise(new Course(), new ExerciseGroup());
    };

    const newParticipation = () => {
        const programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.commitHash = 'Hash commit';
        programmingSubmission.buildFailed = false;
        programmingSubmission.buildArtifact = false;

        const participation = new ProgrammingExerciseStudentParticipation();
        participation.submissions = [programmingSubmission];

        return participation;
    };

    it('should initialize with unlocked repository', () => {
        const exercise = newExercise();
        component.exercise = exercise;

        fixture.detectChanges();

        expect(domainServiceSetDomainSpy).toHaveBeenCalledOnce();
        expect(domainServiceSetDomainSpy).toHaveBeenCalledWith([DomainType.PARTICIPATION, { exercise }]);

        expect(component.repositoryIsLocked).toBeFalse();
        expect(component.getExercise()).toEqual(newExercise());
    });

    it('should set the repositoryIsLocked value to true', () => {
        const programmingExercise = newExercise();
        programmingExercise.dueDate = dayjs().subtract(10, 'seconds');
        programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().subtract(60, 'seconds');

        component.exercise = programmingExercise;
        fixture.detectChanges();

        expect(component.repositoryIsLocked).toBeTrue();
    });

    it('should change state on commit', () => {
        component.studentParticipation = newParticipation();

        component.onCommitStateChange(CommitState.UNDEFINED);
        expect(component.hasSubmittedOnce).toBeFalse();

        component.onCommitStateChange(CommitState.CLEAN);

        // After the first call with CommitState.CLEAN, component.hasSubmittedOnce must be now true
        expect(component.hasSubmittedOnce).toBeTrue();

        component.onCommitStateChange(CommitState.CLEAN);

        expect(component.studentParticipation.submissions![0].submitted).toBeTrue();
        expect(component.studentParticipation.submissions![0].isSynced).toBeTrue();
    });

    it('should desync on file change', () => {
        component.studentParticipation = newParticipation();

        component.studentParticipation.submissions![0].isSynced = true;
        component.onFileChanged();

        expect(component.studentParticipation.submissions![0].isSynced).toBeFalse();
    });

    it('should get submission', () => {
        const participation = newParticipation();
        component.studentParticipation = participation;

        expect(component.getSubmission()).toEqual(participation.submissions![0]);
    });

    it('should return false if no unsaved changes', () => {
        const exercise = newExercise();

        exercise.allowOfflineIde = true;
        exercise.allowOnlineEditor = false;
        component.exercise = exercise;

        expect(component.hasUnsavedChanges()).toBeFalse();
    });
});
