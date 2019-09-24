import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { CourseExerciseService } from 'app/entities/course';
import { SinonStub, stub } from 'sinon';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockJavaBridgeService } from '../../../mocks/mock-java-bridge.service';
import { MockCourseExerciseService } from '../../../mocks/mock-course-exercise.service';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../../helpers/mock-alert.service';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisOverviewModule, CourseExerciseRowComponent } from 'app/overview';
import { MockParticipationWebsocketService } from '../../../mocks';
import { ArtemisResultModule, Result } from 'app/entities/result';
import { IntellijModule } from 'app/intellij/intellij.module';
import { DeviceDetectorService } from 'ngx-device-detector';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise';
import { of } from 'rxjs';
import { InitializationState, StudentParticipation } from 'app/entities/participation';
import { AccountService } from 'app/core';
import { MockAccountService } from '../../../mocks/mock-account.service';
import * as moment from 'moment';
import { QuizExercise } from 'app/entities/quiz-exercise';

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
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, ArtemisSharedModule, ArtemisResultModule, IntellijModule, ArtemisOverviewModule],
            providers: [
                DeviceDetectorService,
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: JavaBridgeService, useClass: MockJavaBridgeService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: JhiAlertService, useClass: MockAlertService },
            ],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseRowComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                getAllParticipationsStub = stub(participationWebsocketService, 'getAllParticipationsForExercise');
            });
    });

    afterEach(() => {
        getAllParticipationsStub.restore();
    });

    it('participationStatus_quiz_quizNotStarted1', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(false, moment(), moment().subtract(3, 'days'), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_STARTED);
    });

    it('participationStatus_quiz_quizNotStarted2', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment(), moment().add(3, 'days'), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_STARTED);
    });

    it('participationStatus_quiz_quizUninitialized', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'days'), moment(), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_UNINITIALIZED);
    });

    it('participationStatus_quiz_quizNotParticipatedA', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment(), moment(), true, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_PARTICIPATED);
    });

    it('participationStatus_quiz_quizActive', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'days'), moment(), true, true, InitializationState.INITIALIZED);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_ACTIVE);
    });

    it('participationStatus_quiz_quizSubmitted', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'days'), moment(), true, true, InitializationState.FINISHED);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_SUBMITTED);
    });

    it('participationStatus_quiz_quizNotParticipatedB', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'days'), moment(), false, true, InitializationState.UNINITIALIZED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_NOT_PARTICIPATED);
    });

    it('participationStatus_quiz_quizFinished', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, moment().add(3, 'days'), moment(), false, true, InitializationState.UNINITIALIZED, true);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.QUIZ_FINISHED);
    });

    it('participationStatus_text_exerciseActive', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.INITIALIZED, true);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_ACTIVE);
    });

    it('participationStatus_text_exerciseMissed', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.INITIALIZED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_MISSED);
    });

    it('participationStatus_text_exerciseSubmitted', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.FINISHED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.EXERCISE_SUBMITTED);
    });

    it('participationStatus_text_uninitialized', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.UNINITIALIZED, false);
        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.UNINITIALIZED);
    });

    it('participationStatus_programming_uninitialized', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment());

        comp.ngOnInit();

        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.UNINITIALIZED);
    });

    it('participationStatus_programming_initialized', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment());

        let studentParticipations = [
            {
                id: 1,
                initializationState: InitializationState.INITIALIZED,
            } as StudentParticipation,
        ];
        comp.exercise.studentParticipations = studentParticipations;

        getAllParticipationsStub.returns(of(studentParticipations));
        comp.ngOnInit();

        expect(comp.exercise.participationStatus).to.equal(ParticipationStatus.INITIALIZED);
    });

    it('participationStatus_programming_inactive', () => {
        setupExercise(ExerciseType.PROGRAMMING, moment());

        let studentParticipations = [
            {
                id: 1,
                initializationState: InitializationState.UNINITIALIZED,
            } as StudentParticipation,
        ];
        comp.exercise.studentParticipations = studentParticipations;

        getAllParticipationsStub.returns(of(studentParticipations));
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
            dueDate: dueDate,
            isPlannedToStart: isPlannedToStart,
            releaseDate: releaseDate,
            type: ExerciseType.QUIZ,
            visibleToStudents: visibleToStudents,
        } as QuizExercise;

        if (hasParticipations) {
            let studentParticipations = [
                {
                    id: 1,
                    initializationState: initializationState,
                } as StudentParticipation,
            ];

            if (hasResults) {
                studentParticipations[0].results = [{ id: 1 } as Result];
            }

            comp.exercise.studentParticipations = studentParticipations;

            getAllParticipationsStub.returns(of(studentParticipations));
        }

        comp.ngOnInit();
    };

    const setupForTestingParticipationStatusExerciseTypeText = (exerciseType: ExerciseType, initializationState: InitializationState, inDueDate: boolean) => {
        let dueDate = inDueDate ? moment().add(3, 'days') : moment().subtract(3, 'days');
        setupExercise(exerciseType, dueDate);

        let studentParticipations = [
            {
                id: 1,
                initializationState: initializationState,
            } as StudentParticipation,
        ];
        comp.exercise.studentParticipations = studentParticipations;

        getAllParticipationsStub.returns(of(studentParticipations));
        comp.ngOnInit();
    };

    const setupExercise = (exerciseType: ExerciseType, dueDate: moment.Moment) => {
        comp.exercise = {
            id: 1,
            type: exerciseType,
            dueDate: dueDate,
        } as Exercise;
    };
});
