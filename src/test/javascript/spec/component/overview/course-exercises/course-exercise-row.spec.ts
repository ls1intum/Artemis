import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { CourseExerciseService, CourseService } from 'app/entities/course/course.service';
import { SinonStub, stub } from 'sinon';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockJavaBridgeService } from '../../../mocks/mock-java-bridge.service';
import { MockCourseExerciseService } from '../../../mocks/mock-course-exercise.service';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../../helpers/mock-alert.service';
import { ArtemisOverviewModule, CourseExerciseRowComponent } from 'app/overview';
import { MockParticipationWebsocketService } from '../../../mocks';
import { Result } from 'app/entities/result';
import { DeviceDetectorService } from 'ngx-device-detector';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise';
import { of } from 'rxjs';
import { InitializationState, StudentParticipation } from 'app/entities/participation';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../mocks/mock-account.service';
import * as moment from 'moment';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { MockCourseService } from '../../../mocks/mock-course.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseExerciseRowComponent', () => {
    let comp: CourseExerciseRowComponent;
    let fixture: ComponentFixture<CourseExerciseRowComponent>;
    let debugElement: DebugElement;
    let getAllParticipationsStub: SinonStub;
    let participationWebsocketService: ParticipationWebsocketService;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, ArtemisOverviewModule],
            providers: [
                DeviceDetectorService,
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: CourseService, useClass: MockCourseService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: JavaBridgeService, useClass: MockJavaBridgeService },
                { provide: JhiAlertService, useClass: MockAlertService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseRowComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                getAllParticipationsStub = stub(participationWebsocketService, 'getParticipationForExercise');
            });
    });

    afterEach(() => {
        getAllParticipationsStub.restore();
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_STARTED if release date is in the past and not planned to start', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(false, moment(), moment().subtract(3, 'minutes'), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_STARTED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_STARTED if release date is in the future and planned to start', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment(), moment().add(3, 'minutes'), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_STARTED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_UNINITIALIZED', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'minutes'), moment(), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_UNINITIALIZED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_PARTICIPATED if there are no participations', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment(), moment(), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_PARTICIPATED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_ACTIVE', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'minutes'), moment(), true, true, InitializationState.INITIALIZED);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_ACTIVE);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_SUBMITTED', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'minutes'), moment(), true, true, InitializationState.FINISHED);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_SUBMITTED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_PARTICIPATED if there are no results', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'minutes'), moment(), false, true, InitializationState.UNINITIALIZED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_PARTICIPATED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_FINISHED', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'minutes'), moment(), false, true, InitializationState.UNINITIALIZED, true);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_FINISHED);
    });

    it('Participation status of text exercise should evaluate to EXERCISE_ACTIVE', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.INITIALIZED, true);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_ACTIVE);
    });

    it('Participation status of text exercise should evaluate to EXERCISE_MISSED', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.INITIALIZED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_MISSED);
    });

    it('Participation status of text exercise should evaluate to EXERCISE_SUBMITTED', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.FINISHED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_SUBMITTED);
    });

    it('Participation status of text exercise should evaluate to UNINITIALIZED', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.UNINITIALIZED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.UNINITIALIZED);
    });

    it('Participation status of programming exercise should evaluate to EXERCISE_MISSED', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment().subtract(1, 'day'));
        comp.ngOnInit();
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_MISSED);
    });

    it('Participation status of programming exercise should evaluate to UNINITIALIZED', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment().add(1, 'day'));
        comp.ngOnInit();
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.UNINITIALIZED);
    });

    it('Participation status of programming exercise should evaluate to INITIALIZED', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment());

        const studentParticipation = {
            id: 1,
            initializationState: InitializationState.INITIALIZED,
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.returns(studentParticipation);
        comp.ngOnInit();

        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.INITIALIZED);
    });

    it('Participation status of programming exercise should evaluate to INACTIVE', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment());

        const studentParticipation = {
            id: 1,
            initializationState: InitializationState.UNINITIALIZED,
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.returns(of(studentParticipation));
        comp.ngOnInit();

        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.INACTIVE);
    });

    const setupForTestingParticipationStatusExerciseTypeQuiz = (
        isPlannedToStart: boolean,
        dueDate: moment.Moment,
        releaseDate: moment.Moment,
        visibleToStudents: boolean,
        hasParticipations: boolean,
        initializationState?: InitializationState,
        hasResults?: boolean,
    ) => {
        comp.exercise = {
            id: 1,
            dueDate,
            isPlannedToStart,
            releaseDate,
            type: ExerciseType.QUIZ,
            visibleToStudents,
        } as QuizExercise;

        if (hasParticipations) {
            const studentParticipation = {
                id: 1,
                initializationState,
            } as StudentParticipation;

            if (hasResults) {
                studentParticipation.results = [{ id: 1 } as Result];
            }

            comp.exercise.studentParticipations = [studentParticipation];

            getAllParticipationsStub.returns(studentParticipation);
        }

        comp.ngOnInit();
    };

    const setupForTestingParticipationStatusExerciseTypeText = (exerciseType: ExerciseType, initializationState: InitializationState, inDueDate: boolean) => {
        const dueDate = inDueDate ? moment().add(3, 'days') : moment().subtract(3, 'days');
        setupExercise(exerciseType, dueDate);

        const studentParticipation = {
            id: 1,
            initializationState,
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.returns(studentParticipation);
        comp.ngOnInit();
    };

    const setupExercise = (exerciseType: ExerciseType, dueDate: moment.Moment) => {
        comp.exercise = {
            id: 1,
            type: exerciseType,
            dueDate,
        } as Exercise;
    };
});
