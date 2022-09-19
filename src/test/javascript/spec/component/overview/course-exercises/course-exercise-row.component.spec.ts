import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, DebugElement } from '@angular/core';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockCourseExerciseService } from '../../../helpers/mocks/service/mock-course-exercise.service';
import { MockParticipationWebsocketService } from '../../../helpers/mocks/service/mock-participation-websocket.service';
import { Result } from 'app/entities/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import dayjs from 'dayjs/esm';
import { MockCourseService } from '../../../helpers/mocks/service/mock-course.service';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { QuizExercise, QuizBatch } from 'app/entities/quiz/quiz-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

@Component({
    template: '',
})
class DummyComponent {}

describe('CourseExerciseRowComponent', () => {
    let comp: CourseExerciseRowComponent;
    let fixture: ComponentFixture<CourseExerciseRowComponent>;
    let debugElement: DebugElement;
    let getAllParticipationsStub: jest.SpyInstance;
    let participationWebsocketService: ParticipationWebsocketService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                TranslateModule.forRoot(),
                NgbModule,
                RouterTestingModule.withRoutes([
                    { path: 'courses/:courseId/exercises', component: DummyComponent },
                    { path: 'courses/:courseId/exercises/:exerciseId', component: DummyComponent },
                ]),
            ],
            declarations: [
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(NotReleasedTagComponent),
                MockComponent(DifficultyBadgeComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(OrionFilterDirective),
                CourseExerciseRowComponent,
                DummyComponent,
            ],
            providers: [
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: CourseManagementService, useClass: MockCourseService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseRowComponent);
                comp = fixture.componentInstance;
                comp.course = { id: 123, isAtLeastInstructor: true } as Course;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                getAllParticipationsStub = jest.spyOn(participationWebsocketService, 'getParticipationForExercise');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_STARTED if release date is in the past and not planned to start', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(false, dayjs().add(1, 'minute'), dayjs().subtract(3, 'minutes'), true, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_NOT_STARTED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_STARTED if release date is in the future and planned to start', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, dayjs().add(5, 'minutes'), dayjs().add(3, 'minutes'), true, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_NOT_STARTED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_UNINITIALIZED', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, dayjs().add(3, 'minutes'), dayjs().subtract(1, 'second'), true, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_UNINITIALIZED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_PARTICIPATED if there are no participations', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, dayjs().subtract(1, 'second'), dayjs().subtract(2, 'second'), true, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_NOT_PARTICIPATED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_ACTIVE', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, dayjs().add(3, 'minutes'), dayjs().subtract(1, 'second'), true, true, InitializationState.INITIALIZED);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_ACTIVE);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_SUBMITTED', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, dayjs().add(3, 'minutes'), dayjs().subtract(1, 'second'), true, true, InitializationState.FINISHED);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_SUBMITTED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_NOT_PARTICIPATED if there are no results', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(
            true,
            dayjs().subtract(1, 'second'),
            dayjs().subtract(2, 'seconds'),
            true,
            true,
            InitializationState.UNINITIALIZED,
            false,
        );
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_NOT_PARTICIPATED);
    });

    it('Participation status of quiz exercise should evaluate to QUIZ_FINISHED', () => {
        setupForTestingParticipationStatusExerciseTypeQuiz(true, dayjs().subtract(3, 'minutes'), dayjs().subtract(1, 'hour'), false, true, InitializationState.UNINITIALIZED, true);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.QUIZ_FINISHED);
    });

    it('Participation status of text exercise should evaluate to EXERCISE_ACTIVE', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.INITIALIZED, true);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.EXERCISE_ACTIVE);
    });

    it('Participation status of text exercise should evaluate to EXERCISE_MISSED', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.INITIALIZED, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.EXERCISE_MISSED);
    });

    it('Participation status of text exercise should evaluate to EXERCISE_SUBMITTED', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.FINISHED, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.EXERCISE_SUBMITTED);
    });

    it('Participation status of text exercise should evaluate to UNINITIALIZED', () => {
        setupForTestingParticipationStatusExerciseTypeText(ExerciseType.TEXT, InitializationState.UNINITIALIZED, false);
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.UNINITIALIZED);
    });

    it('Participation status of programming exercise should evaluate to EXERCISE_MISSED', () => {
        setupExercise(ExerciseType.PROGRAMMING, dayjs().subtract(1, 'day'));
        getAllParticipationsStub.mockReturnValue([]);

        comp.ngOnChanges();
        comp.ngOnInit();
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.EXERCISE_MISSED);
    });

    it('Participation status of programming exercise should evaluate to UNINITIALIZED', () => {
        setupExercise(ExerciseType.PROGRAMMING, dayjs().add(1, 'day'));
        getAllParticipationsStub.mockReturnValue([]);

        comp.ngOnChanges();
        comp.ngOnInit();
        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.UNINITIALIZED);
    });

    it('Participation status of programming exercise should evaluate to INITIALIZED', () => {
        setupExercise(ExerciseType.PROGRAMMING, dayjs());

        const studentParticipation = {
            id: 1,
            initializationState: InitializationState.INITIALIZED,
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.mockReturnValue([studentParticipation]);
        comp.ngOnChanges();
        comp.ngOnInit();

        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.INITIALIZED);
    });

    it('Participation status of programming exercise should evaluate to EXERCISE_MISSED with uninitialized participation', () => {
        setupExercise(ExerciseType.PROGRAMMING, dayjs().subtract(1, 'day'));

        const studentParticipation = {
            id: 1,
            initializationState: InitializationState.UNINITIALIZED,
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.mockReturnValue([studentParticipation]);
        comp.ngOnChanges();
        comp.ngOnInit();

        expect(comp.exercise.participationStatus).toBe(ParticipationStatus.EXERCISE_MISSED);
    });

    const setupForTestingParticipationStatusExerciseTypeQuiz = (
        isPlannedToStart: boolean,
        dueDate: dayjs.Dayjs,
        releaseDate: dayjs.Dayjs,
        visibleToStudents: boolean,
        hasParticipations: boolean,
        initializationState?: InitializationState,
        hasResults?: boolean,
        duration = 10,
    ) => {
        comp.exercise = {
            id: 1,
            dueDate,
            quizBatches: isPlannedToStart
                ? [{ startTime: releaseDate, started: isPlannedToStart && releaseDate.isBefore(dayjs()), ended: dueDate.isBefore(dayjs()) } as QuizBatch]
                : [],
            releaseDate: visibleToStudents ? releaseDate : undefined,
            type: ExerciseType.QUIZ,
            visibleToStudents,
            quizStarted: isPlannedToStart && visibleToStudents && releaseDate.isBefore(dayjs()),
            quizEnded: dueDate.isBefore(dayjs()),
            duration,
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

            getAllParticipationsStub.mockReturnValue([studentParticipation]);
        } else {
            getAllParticipationsStub.mockReturnValue([]);
        }

        comp.ngOnChanges();
        comp.ngOnInit();
    };

    const setupForTestingParticipationStatusExerciseTypeText = (exerciseType: ExerciseType, initializationState: InitializationState, inDueDate: boolean) => {
        const dueDate = inDueDate ? dayjs().add(3, 'days') : dayjs().subtract(3, 'days');
        setupExercise(exerciseType, dueDate);

        const studentParticipation = {
            id: 1,
            initializationState,
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.mockReturnValue([studentParticipation]);
        comp.ngOnChanges();
        comp.ngOnInit();
    };

    const setupExercise = (exerciseType: ExerciseType, dueDate: dayjs.Dayjs) => {
        comp.exercise = {
            id: 1,
            type: exerciseType,
            dueDate,
        } as Exercise;
    };
});
