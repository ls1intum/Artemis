import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { Exercise, ParticipationStatus } from 'app/entities/exercise';
import { InitializationState, ProgrammingExerciseStudentParticipation, StudentParticipation } from 'app/entities/participation';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockJavaBridgeService } from '../../../mocks/mock-java-bridge.service';
import { MockCourseExerciseService } from '../../../mocks/mock-course-exercise.service';
import { IntelliJState } from 'app/intellij/intellij';
import { BehaviorSubject, Subject } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../../helpers/mock-alert.service';
import { ArtemisSharedModule } from 'app/shared';
import { IntellijModule } from 'app/intellij/intellij.module';
import { MockComponent } from 'ng-mocks';
import { ExerciseActionButtonComponent, ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview';
import { IdeBuildAndTestService } from 'app/intellij/ide-build-and-test.service';
import { MockIdeBuildAndTestService } from '../../../mocks/mock-ide-build-and-test.service';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../../mocks/mock-feature-toggle-service';
import { stringifyCircular } from 'app/shared/util/utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseStudentIdeActionsComponent', () => {
    let comp: ProgrammingExerciseStudentIdeActionsComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentIdeActionsComponent>;
    let debugElement: DebugElement;
    let javaBridge: JavaBridgeService;
    let courseExerciseService: CourseExerciseService;
    let ideBuildService: IdeBuildAndTestService;

    let startExerciseStub: SinonStub;
    let ideStateStub: SinonStub;
    let cloneSpy: SinonSpy;
    let submitSpy: SinonSpy;
    let forwardBuildSpy: SinonSpy;

    const exercise = { id: 42 } as Exercise;
    const ideState = { opened: 40, building: false, cloning: false } as IntelliJState;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, IntellijModule, ArtemisSharedModule, FeatureToggleModule],
            declarations: [ProgrammingExerciseStudentIdeActionsComponent, MockComponent(ExerciseActionButtonComponent)],
            providers: [
                { provide: IdeBuildAndTestService, useClass: MockIdeBuildAndTestService },
                { provide: JavaBridgeService, useClass: MockJavaBridgeService },
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
                javaBridge = debugElement.injector.get(JavaBridgeService);
                ideBuildService = debugElement.injector.get(IdeBuildAndTestService);
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                startExerciseStub = stub(courseExerciseService, 'startExercise');
                forwardBuildSpy = spy(ideBuildService, 'listenOnBuildOutputAndForwardChanges');
                cloneSpy = spy(javaBridge, 'clone');
                submitSpy = spy(javaBridge, 'submit');
                ideStateStub = stub(javaBridge, 'state');
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

        expect(comp.participationStatus()).to.be.equal(ParticipationStatus.INACTIVE);
        expect(startExerciseStub).to.have.been.calledOnce;
        participationSubject.next(initPart);

        fixture.detectChanges();
        tick();

        expect(comp.participationStatus()).to.be.equal(ParticipationStatus.INITIALIZED);

        fixture.destroy();
        flush();
    }));

    it('should clone the correct repository in the IDE', () => {
        const participation = { id: 123, repositoryUrl: 'testUrl' } as ProgrammingExerciseStudentParticipation;
        const progExercise = { id: 42, title: 'Test Title' } as Exercise;
        progExercise.studentParticipations = [participation];
        const exerciseJson = stringifyCircular(progExercise);
        comp.exercise = progExercise;
        comp.courseId = 456;

        comp.importIntoIntelliJ();
        expect(cloneSpy).to.have.been.calledOnceWithExactly('testUrl', exerciseJson);
    });

    it('should submit the changes and then forward the build results on submit', () => {
        comp.exercise = exercise;
        comp.submitChanges();

        expect(submitSpy).to.have.been.calledOnce;
        expect(forwardBuildSpy).to.have.been.calledOnce;
        expect(forwardBuildSpy).to.have.been.calledImmediatelyAfter(submitSpy);
    });
});
