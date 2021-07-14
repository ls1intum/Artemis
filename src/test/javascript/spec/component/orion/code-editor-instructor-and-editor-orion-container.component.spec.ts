import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { CodeEditorInstructorAndEditorOrionContainerComponent } from 'app/orion/management/code-editor-instructor-and-editor-orion-container.component';
import { ArtemisTestModule } from '../../test.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TestBed } from '@angular/core/testing';
import { REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { spy, stub } from 'sinon';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-exercise-status.component';
import { ExerciseHintStudentComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { BehaviorSubject } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorInstructorAndEditorOrionContainerComponent', () => {
    let comp: CodeEditorInstructorAndEditorOrionContainerComponent;

    let orionConnectorService: OrionConnectorService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, OrionModule],
            declarations: [
                CodeEditorInstructorAndEditorOrionContainerComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(AlertComponent),
                MockComponent(ProgrammingExerciseInstructorExerciseStatusComponent),
                MockComponent(ExerciseHintStudentComponent),
                MockComponent(ProgrammingExerciseEditableInstructionComponent),
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(OrionConnectorService),
                MockProvider(OrionBuildAndTestService),
                MockProvider(TranslateService),
                MockProvider(LocalStorageService),
                MockProvider(SessionStorageService),
            ],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(CodeEditorInstructorAndEditorOrionContainerComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
            });
    });

    it('applyDomainChange should inform connector', () => {
        const selectRepositorySpy = spy(orionConnectorService, 'selectRepository');

        // @ts-ignore
        comp.applyDomainChange({}, {});

        expect(selectRepositorySpy).to.have.been.calledOnceWithExactly(REPOSITORY.TEST);
    });
    it('ngOnInit should subscribe to orionState', () => {
        const orionStateStub = stub(orionConnectorService, 'state');
        const orionState = { opened: 40, building: false, cloning: false } as any;
        orionStateStub.returns(new BehaviorSubject(orionState));

        comp.ngOnInit();

        expect(orionStateStub).to.have.been.calledOnceWithExactly();
        expect(comp.orionState).to.be.deep.equals(orionState);
    });
    it('buildLocally should call connector', () => {
        const buildLocallySpy = spy(orionConnectorService, 'buildAndTestLocally');
        const isBuildingSpy = spy(orionConnectorService, 'isBuilding');

        comp.buildLocally();

        expect(isBuildingSpy).to.have.been.calledOnceWithExactly(true);
        expect(buildLocallySpy).to.have.been.calledOnceWithExactly();
    });
    it('submit should call connector', () => {
        const submitSpy = spy(orionConnectorService, 'submit');
        const isBuildingSpy = spy(orionConnectorService, 'isBuilding');
        const listenOnBuildOutputSpy = spy(TestBed.inject(OrionBuildAndTestService), 'listenOnBuildOutputAndForwardChanges');

        const exercise = { id: 5 } as any;
        const participation = { id: 10 } as any;
        comp.selectedRepository = REPOSITORY.SOLUTION;
        comp.exercise = exercise;
        comp.selectedParticipation = participation;

        comp.submit();

        expect(submitSpy).to.have.been.calledOnceWithExactly();
        expect(isBuildingSpy).to.have.been.calledOnceWithExactly(true);
        expect(listenOnBuildOutputSpy).to.have.been.calledOnceWithExactly(exercise, participation);
    });
});
