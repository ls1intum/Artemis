import * as chai from 'chai';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, of } from 'rxjs';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisOrionConnector, OrionState } from 'app/shared/orion/orion';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ActivatedRoute } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionExerciseDetailsStudentActionsComponent', () => {
    let comp: OrionExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<OrionExerciseDetailsStudentActionsComponent>;
    let orionConnector: ArtemisOrionConnector;

    let ideStateStub: SinonStub;
    let cloneSpy: SinonSpy;
    let submitSpy: SinonSpy;
    let forwardBuildSpy: SinonSpy;

    const exercise = { id: 42 } as Exercise;
    const ideState = { opened: 40, building: false, cloning: false } as OrionState;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                OrionExerciseDetailsStudentActionsComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(OrionButtonComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(OrionBuildAndTestService),
                MockProvider(OrionConnectorService),
                MockProvider(FeatureToggleService),
                { provide: ActivatedRoute, useValue: { queryParams: of({ withIdeSubmit: true }) } },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionExerciseDetailsStudentActionsComponent);
                comp = fixture.componentInstance;
                orionConnector = TestBed.inject(OrionConnectorService);
                forwardBuildSpy = spy(TestBed.inject(OrionBuildAndTestService), 'listenOnBuildOutputAndForwardChanges');
                cloneSpy = spy(orionConnector, 'importParticipation');
                submitSpy = spy(orionConnector, 'submit');
                ideStateStub = stub(orionConnector, 'state');
            });
    });
    afterEach(() => {
        sinon.restore();
    });

    it('should not reflect that the represented exercise is opened if another exercise has been opened', fakeAsync(() => {
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        ideStateStub.returns(stateObservable);

        comp.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(comp.orionState.opened).to.not.equal(exercise.id);

        fixture.destroy();
        flush();
    }));
    it('should reflect that the represented exercise is opened if the same exercise is open in the IDE', fakeAsync(() => {
        const stateObservable = new BehaviorSubject({ opened: exercise.id });
        comp.exercise = exercise;
        ideStateStub.returns(stateObservable);

        comp.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(comp.orionState.opened).to.equal(exercise.id);

        fixture.destroy();
        flush();
    }));
    it('should clone the correct repository in the IDE', () => {
        const participation = { id: 123, repositoryUrl: 'testUrl' } as ProgrammingExerciseStudentParticipation;
        const progExercise = { id: 42, title: 'Test Title' } as Exercise;
        progExercise.studentParticipations = [participation];
        comp.exercise = progExercise;
        comp.courseId = 456;

        comp.importIntoIDE();
        expect(cloneSpy).to.have.been.calledOnceWithExactly('testUrl', progExercise);
    });
    it('should submit the changes and then forward the build results on submit', () => {
        comp.exercise = exercise;
        comp.submitChanges();

        expect(submitSpy).to.have.been.calledOnce;
        expect(forwardBuildSpy).to.have.been.calledOnce;
        expect(forwardBuildSpy).to.have.been.calledImmediatelyAfter(submitSpy);
    });
    it('isOfflineIdeAllowed should work', () => {
        comp.exercise = { allowOfflineIde: true } as any;

        expect(comp.isOfflineIdeAllowed).to.be.true;
    });
    it('should submit if stated in route', fakeAsync(() => {
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        ideStateStub.returns(stateObservable);
        const submitChangesSpy = spy(comp, 'submitChanges');

        comp.ngOnInit();
        tick();

        expect(submitChangesSpy).to.have.been.calledOnceWith();
    }));
});
