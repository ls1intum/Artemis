import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockComponent } from 'ng-mocks';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTestModule } from '../../test.module';
import { MockOrionConnectorService } from '../../helpers/mocks/service/mock-orion-connector.service';
import { ArtemisOrionConnector, OrionState } from 'app/shared/orion/orion';
import { OrionModule } from 'app/shared/orion/orion.module';
import { MockIdeBuildAndTestService } from '../../helpers/mocks/service/mock-ide-build-and-test.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionExerciseDetailsStudentActionsComponent', () => {
    let comp: OrionExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<OrionExerciseDetailsStudentActionsComponent>;
    let orionConnector: ArtemisOrionConnector;
    let ideBuildService: OrionBuildAndTestService;

    let ideStateStub: SinonStub;
    let cloneSpy: SinonSpy;
    let submitSpy: SinonSpy;
    let forwardBuildSpy: SinonSpy;

    const exercise = { id: 42 } as Exercise;
    const ideState = { opened: 40, building: false, cloning: false } as OrionState;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, OrionModule, ArtemisSharedModule, FeatureToggleModule],
            declarations: [OrionExerciseDetailsStudentActionsComponent, MockComponent(ExerciseActionButtonComponent), MockComponent(ExerciseDetailsStudentActionsComponent)],
            providers: [
                { provide: OrionBuildAndTestService, useClass: MockIdeBuildAndTestService },
                { provide: OrionConnectorService, useClass: MockOrionConnectorService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionExerciseDetailsStudentActionsComponent);
                comp = fixture.componentInstance;
                orionConnector = TestBed.inject(OrionConnectorService);
                ideBuildService = TestBed.inject(OrionBuildAndTestService);
                forwardBuildSpy = spy(ideBuildService, 'listenOnBuildOutputAndForwardChanges');
                cloneSpy = spy(orionConnector, 'importParticipation');
                submitSpy = spy(orionConnector, 'submit');
                ideStateStub = stub(orionConnector, 'state');
            });
    });

    afterEach(() => {
        forwardBuildSpy.restore();
        cloneSpy.restore();
        submitSpy.restore();
        ideStateStub.restore();
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
});
