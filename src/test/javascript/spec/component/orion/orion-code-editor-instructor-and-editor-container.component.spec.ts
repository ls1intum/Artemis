import { CodeEditorInstructorAndEditorOrionContainerComponent } from 'app/orion/management/code-editor-instructor-and-editor-orion-container.component';
import { ArtemisTestModule } from '../../test.module';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TestBed } from '@angular/core/testing';
import { REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-exercise-status.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { BehaviorSubject } from 'rxjs';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

describe('CodeEditorInstructorAndEditorOrionContainerComponent', () => {
    let comp: CodeEditorInstructorAndEditorOrionContainerComponent;
    let orionConnectorService: OrionConnectorService;
    let orionBuildAndTestService: OrionBuildAndTestService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CodeEditorInstructorAndEditorOrionContainerComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(ProgrammingExerciseInstructorExerciseStatusComponent),
                MockComponent(ProgrammingExerciseEditableInstructionComponent),
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockComponent(OrionButtonComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(OrionConnectorService),
                MockProvider(OrionBuildAndTestService),
                { provide: Router, useClass: MockRouter },
                MockProvider(ProgrammingExerciseService),
                MockProvider(CourseExerciseService),
                MockProvider(DomainService),
                MockProvider(ProgrammingExerciseParticipationService),
                MockProvider(Location),
                MockProvider(ParticipationService),
            ],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(CodeEditorInstructorAndEditorOrionContainerComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
                orionBuildAndTestService = TestBed.inject(OrionBuildAndTestService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('applyDomainChange should inform connector', () => {
        const selectRepositorySpy = jest.spyOn(orionConnectorService, 'selectRepository');

        // @ts-ignore
        comp.applyDomainChange({}, {});

        expect(selectRepositorySpy).toHaveBeenCalledOnce();
        expect(selectRepositorySpy).toHaveBeenCalledWith(REPOSITORY.TEST);
    });

    it('ngOnInit should subscribe to orionState', () => {
        const orionState = { opened: 40, building: false, cloning: false } as any;
        const orionStateStub = jest.spyOn(orionConnectorService, 'state').mockReturnValue(new BehaviorSubject(orionState));

        comp.ngOnInit();

        expect(orionStateStub).toHaveBeenCalledOnce();
        expect(orionStateStub).toHaveBeenCalledWith();
        expect(comp.orionState).toEqual(orionState);
    });

    it('buildLocally should call connector', () => {
        const buildLocallySpy = jest.spyOn(orionConnectorService, 'buildAndTestLocally');
        const isBuildingSpy = jest.spyOn(orionConnectorService, 'isBuilding');

        comp.buildLocally();

        expect(isBuildingSpy).toHaveBeenCalledOnce();
        expect(isBuildingSpy).toHaveBeenCalledWith(true);
        expect(buildLocallySpy).toHaveBeenCalledOnce();
        expect(buildLocallySpy).toHaveBeenCalledWith();
    });

    it('submit should call connector', () => {
        const submitSpy = jest.spyOn(orionConnectorService, 'submit');
        const isBuildingSpy = jest.spyOn(orionConnectorService, 'isBuilding');
        const listenOnBuildOutputSpy = jest.spyOn(orionBuildAndTestService, 'listenOnBuildOutputAndForwardChanges');

        const exercise = { id: 5 } as any;
        const participation = { id: 10 } as any;
        comp.selectedRepository = REPOSITORY.SOLUTION;
        comp.exercise = exercise;
        comp.selectedParticipation = participation;

        comp.submit();

        expect(submitSpy).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledWith();
        expect(isBuildingSpy).toHaveBeenCalledOnce();
        expect(isBuildingSpy).toHaveBeenCalledWith(true);
        expect(listenOnBuildOutputSpy).toHaveBeenCalledOnce();
        expect(listenOnBuildOutputSpy).toHaveBeenCalledWith(exercise, participation);
    });
});
