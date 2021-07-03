import * as chai from 'chai';

import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject, Subject } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockComponent } from 'ng-mocks';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview/exercise-details/programming-exercise-student-ide-actions.component';
import { InitializationState } from 'app/entities/participation/participation.model';
import { MockFeatureToggleService } from '../../../helpers/mocks/service/mock-feature-toggle.service';
import { Exercise, ParticipationStatus } from 'app/entities/exercise.model';
import { MockCourseExerciseService } from '../../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisTestModule } from '../../../test.module';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { MockOrionConnectorService } from '../../../helpers/mocks/service/mock-orion-connector.service';
import { ArtemisOrionConnector, OrionState } from 'app/shared/orion/orion';
import { OrionModule } from 'app/shared/orion/orion.module';
import { MockIdeBuildAndTestService } from '../../../helpers/mocks/service/mock-ide-build-and-test.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseStudentIdeActionsComponent', () => {
    let comp: ProgrammingExerciseStudentIdeActionsComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentIdeActionsComponent>;
    let debugElement: DebugElement;
    let orionConnector: ArtemisOrionConnector;
    let courseExerciseService: CourseExerciseService;
    let ideBuildService: OrionBuildAndTestService;

    let startExerciseStub: SinonStub;
    let ideStateStub: SinonStub;
    let cloneSpy: SinonSpy;
    let submitSpy: SinonSpy;
    let forwardBuildSpy: SinonSpy;

    const exercise = { id: 42 } as Exercise;
    const ideState = { opened: 40, building: false, cloning: false } as OrionState;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, OrionModule, ArtemisSharedModule, FeatureToggleModule],
            declarations: [ProgrammingExerciseStudentIdeActionsComponent, MockComponent(ExerciseActionButtonComponent)],
            providers: [
                { provide: OrionBuildAndTestService, useClass: MockIdeBuildAndTestService },
                { provide: OrionConnectorService, useClass: MockOrionConnectorService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseStudentIdeActionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                orionConnector = debugElement.injector.get(OrionConnectorService);
                ideBuildService = debugElement.injector.get(OrionBuildAndTestService);
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                startExerciseStub = stub(courseExerciseService, 'startExercise');
                forwardBuildSpy = spy(ideBuildService, 'listenOnBuildOutputAndForwardChanges');
                cloneSpy = spy(orionConnector, 'importParticipation');
                submitSpy = spy(orionConnector, 'submit');
                ideStateStub = stub(orionConnector, 'state');
            });
    });

    afterEach(() => {
        startExerciseStub.restore();
        cloneSpy.restore();
        submitSpy.restore();
    });

    it('should not reflect that the represented exercise is opened if another exercise has been opened', fakeAsync(() => {
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        ideStateStub.returns(stateObservable);

        comp.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(comp.ideState.opened).to.not.equal(exercise.id);

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

        expect(comp.ideState.opened).to.equal(exercise.id);

        fixture.destroy();
        flush();
    }));

    it('should reflect the correct participation state', fakeAsync(() => {
        const inactivePart = { id: 2, initializationState: InitializationState.INACTIVE } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        startExerciseStub.returns(participationSubject);
        ideStateStub.returns(stateObservable);
        comp.startExercise();
        participationSubject.next(inactivePart);

        fixture.detectChanges();
        tick();

        expect(comp.participationStatus(comp.exercise)).to.be.equal(ParticipationStatus.INACTIVE);
        expect(startExerciseStub).to.have.been.calledOnce;
        participationSubject.next(initPart);

        fixture.detectChanges();
        tick();

        expect(comp.participationStatus(comp.exercise)).to.be.equal(ParticipationStatus.INITIALIZED);

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
