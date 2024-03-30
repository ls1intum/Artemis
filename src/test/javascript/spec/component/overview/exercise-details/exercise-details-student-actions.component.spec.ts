import { HttpClient } from '@angular/common/http';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core/user/user.model';
import { Exercise, ExerciseMode, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizBatch, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Result } from 'app/entities/result.model';
import { Team } from 'app/entities/team.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { StartPracticeModeButtonComponent } from 'app/shared/components/start-practice-mode-button/start-practice-mode-button.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Subject, of } from 'rxjs';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockCourseExerciseService } from '../../../helpers/mocks/service/mock-course-exercise.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../../test.module';

describe('ExerciseDetailsStudentActionsComponent', () => {
    let comp: ExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<ExerciseDetailsStudentActionsComponent>;
    let debugElement: DebugElement;
    let courseExerciseService: CourseExerciseService;
    let profileService: ProfileService;
    let startExerciseStub: jest.SpyInstance;
    let resumeStub: jest.SpyInstance;
    let getProfileInfoSub: jest.SpyInstance;
    let router: MockRouter;

    const team = { id: 1, students: [{ id: 99 } as User] } as Team;
    const exercise: Exercise = {
        id: 42,
        type: ExerciseType.PROGRAMMING,
        studentParticipations: [],
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const teamExerciseWithoutTeamAssigned: Exercise = {
        ...exercise,
        mode: ExerciseMode.TEAM,
        teamMode: true,
        studentAssignedTeamIdComputed: true,
    };
    const teamExerciseWithTeamAssigned = { ...teamExerciseWithoutTeamAssigned, studentAssignedTeamId: team.id, allowOfflineIde: true } as ProgrammingExercise;

    const testRunParticipation = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as ProgrammingExerciseStudentParticipation;

    const testRunExercise = {
        id: 45,
        type: ExerciseType.PROGRAMMING,
        allowOfflineIde: true,
        studentParticipations: [testRunParticipation],
        exerciseGroup: {},
    } as ProgrammingExercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbTooltipModule)],
            declarations: [
                ExerciseDetailsStudentActionsComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(CloneRepoButtonComponent),
                MockComponent(StartPracticeModeButtonComponent),
                MockPipe(ArtemisTranslatePipe, (query: any, args?: any) => query + (args ? args : '')),
                ExtensionPointDirective,
                MockRouterLinkDirective,
                MockDirective(FeatureToggleDirective),
            ],
            providers: [
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: Router, useClass: MockRouter },
                MockProvider(HttpClient),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseDetailsStudentActionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                profileService = debugElement.injector.get(ProfileService);
                router = debugElement.injector.get(Router) as unknown as MockRouter;

                getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoSub.mockReturnValue(of({ inProduction: false, sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo));

                startExerciseStub = jest.spyOn(courseExerciseService, 'startExercise');
                resumeStub = jest.spyOn(courseExerciseService, 'resumeProgrammingExercise');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.PROGRAMMING, ExerciseType.TEXT])(
        'should not show the buttons "Team" and "Start exercise" for a team exercise when not assigned to a team yet',
        fakeAsync((exerciseType: ExerciseType) => {
            comp.exercise = { ...teamExerciseWithoutTeamAssigned, type: exerciseType };
            comp.ngOnChanges();
            fixture.detectChanges();
            tick();

            const viewTeamButton = fixture.debugElement.query(By.css('.view-team'));
            expect(viewTeamButton).toBeNull();

            const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
            expect(startExerciseButton).toBeNull();
        }),
    );

    it.each([ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.PROGRAMMING])(
        'should show the buttons "Team" and "Start exercise" for a team exercise for a student to view his team when assigned to a team',
        fakeAsync((exerciseType: ExerciseType) => {
            comp.exercise = { ...teamExerciseWithTeamAssigned, type: exerciseType };
            comp.ngOnChanges();
            fixture.detectChanges();
            tick();

            const viewTeamButton = fixture.debugElement.query(By.css('.view-team'));
            expect(viewTeamButton).not.toBeNull();
            const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
            expect(startExerciseButton).not.toBeNull();
        }),
    );

    it('should create the correct repository URL for missing exerciseID in URL', () => {
        // Set up necessary data for the test
        const courseId = 123; // Example course ID
        const exerciseId = 456; // Example exercise ID
        const repositoryUrl = `/courses/${courseId}/exercises`;
        const expectedRepositoryLink = `/courses/${courseId}/exercises/${exerciseId}`;
        router.setUrl(repositoryUrl);

        // Assign the courseId and exerciseId to the component's input properties
        comp.courseId = courseId;
        comp.exercise = { id: exerciseId } as Exercise;

        // Call the ngOnInit method to initialize the component
        comp.ngOnInit();

        // Assert that the repositoryLink property is set correctly
        expect(comp.repositoryLink).toBe(expectedRepositoryLink);
    });

    it('should create the correct repository URL for exam exercises', () => {
        // Set up necessary data for the test
        const courseId = 123; // Example course ID
        const exerciseId = 456; // Example exercise ID
        const examId = 789; // Example exam ID
        const repositoryUrl = `/courses/${courseId}/exams/${examId}`;
        const expectedRepositoryLink = `/courses/${courseId}/exams/${examId}/exercises/${exerciseId}`;
        router.setUrl(repositoryUrl);

        // Assign the courseId and exerciseId to the component's input properties
        comp.courseId = courseId;
        comp.exercise = { id: exerciseId } as Exercise;

        // Call the ngOnInit method to initialize the component
        comp.ngOnInit();

        // Assert that the repositoryLink property is set correctly
        expect(comp.repositoryLink).toBe(expectedRepositoryLink);
    });

    it('should reflect the correct participation state when team exercise was started', fakeAsync(() => {
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        comp.exercise = teamExerciseWithTeamAssigned;
        startExerciseStub.mockReturnValue(participationSubject);
        comp.startExercise();
        participationSubject.next(inactivePart);

        fixture.detectChanges();
        tick();

        expect(comp.gradedParticipation?.initializationState).toEqual(InitializationState.UNINITIALIZED);

        expect(startExerciseStub).toHaveBeenCalledOnce();
        participationSubject.next(initPart);

        fixture.detectChanges();
        tick();

        expect(comp.gradedParticipation?.initializationState).toEqual(InitializationState.INITIALIZED);

        // Check that button "Start exercise" is no longer shown
        const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
        expect(startExerciseButton).toBeNull();

        // Check that button "Clone repository" is not shown (repo_url is null)
        const cloneRepositoryButton = fixture.debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepositoryButton).toBeNull();

        fixture.destroy();
        flush();
    }));

    it('should reflect the correct participation state for practice mode', fakeAsync(() => {
        const exercise = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            allowOfflineIde: true,
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;

        comp.exercise = exercise;

        fixture.detectChanges();
        tick();

        let startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).not.toBeNull();

        let cloneRepositoryButton = fixture.debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepositoryButton).toBeNull();

        comp.exercise.studentParticipations = [initPart];
        comp.practiceParticipation = initPart;

        fixture.detectChanges();
        tick();

        startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).toBeNull();

        cloneRepositoryButton = fixture.debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepositoryButton).toBeNull();

        fixture.destroy();
        flush();
    }));

    it('should correctly not show the clone repository button for exam test runs', fakeAsync(() => {
        testRunParticipation.repositoryUri = undefined;
        testRunExercise.studentParticipations = [testRunParticipation];

        comp.examMode = true;
        comp.exercise = testRunExercise;
        comp.practiceParticipation = testRunParticipation;

        fixture.detectChanges();
        tick();

        const startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).toBeNull();

        const cloneRepositoryButton = fixture.debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepositoryButton).toBeNull();

        fixture.destroy();
        flush();
    }));

    it('should correctly show the clone repository button for exam test runs', fakeAsync(() => {
        testRunParticipation.repositoryUri = 'https://clone-me.git';
        testRunExercise.studentParticipations = [testRunParticipation];

        comp.examMode = true;
        comp.exercise = testRunExercise;
        comp.practiceParticipation = testRunParticipation;

        fixture.detectChanges();
        tick();

        const startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).toBeNull();

        const cloneRepositoryButton = fixture.debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepositoryButton).not.toBeNull();

        fixture.destroy();
        flush();
    }));

    it('should correctly resume programming participation', () => {
        const inactiveParticipation: ProgrammingExerciseStudentParticipation = { id: 1, initializationState: InitializationState.INACTIVE };
        const activeParticipation: ProgrammingExerciseStudentParticipation = { id: 1, initializationState: InitializationState.INITIALIZED };
        const practiceParticipation: ProgrammingExerciseStudentParticipation = { id: 2, testRun: true, initializationState: InitializationState.INACTIVE };
        comp.exercise = { id: 3, studentParticipations: [inactiveParticipation, practiceParticipation] } as ProgrammingExercise;
        comp.updateParticipations();

        resumeStub.mockReturnValue(of(activeParticipation));

        comp.resumeProgrammingExercise(false);

        expect(comp.exercise.studentParticipations).toEqual([activeParticipation, practiceParticipation]);
    });

    it('should disable the feedback request button', () => {
        const result: Result = { score: 50, rated: true };
        const participation: StudentParticipation = {
            results: [result],
            individualDueDate: undefined,
        };

        comp.exercise = { ...exercise, allowManualFeedbackRequests: true };
        comp.gradedParticipation = participation;

        expect(comp.isFeedbackRequestButtonDisabled()).toBeTrue();
    });

    it('should enable the feedback request button', () => {
        const result: Result = { score: 100, rated: true };
        const participation: StudentParticipation = {
            results: [result],
            individualDueDate: undefined,
        };

        comp.exercise = { ...exercise, allowManualFeedbackRequests: true };
        comp.gradedParticipation = participation;

        expect(comp.isFeedbackRequestButtonDisabled()).toBeFalse();
    });

    it('should show correct buttons in exam mode', fakeAsync(() => {
        const exercise = { type: ExerciseType.PROGRAMMING, allowOfflineIde: false, allowOnlineEditor: true } as ProgrammingExercise;
        exercise.studentParticipations = [{ initializationState: InitializationState.INITIALIZED } as StudentParticipation];
        comp.exercise = exercise;
        comp.examMode = true;
        comp.updateParticipations();

        fixture.detectChanges();
        tick();

        let startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        let codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        let cloneRepoButton = debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepoButton).toBeNull();

        exercise.allowOfflineIde = true;

        fixture.detectChanges();
        tick();

        startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        cloneRepoButton = debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepoButton).toBeNull();
    }));

    it('should show correct buttons in exam mode, including clone repo button', fakeAsync(() => {
        const exercise = { type: ExerciseType.PROGRAMMING, allowOfflineIde: false, allowOnlineEditor: true } as ProgrammingExercise;
        exercise.studentParticipations = [
            { initializationState: InitializationState.INITIALIZED, repositoryUri: 'https://clone-me.git' } as ProgrammingExerciseStudentParticipation,
        ];
        comp.exercise = exercise;
        comp.examMode = true;
        comp.updateParticipations();

        fixture.detectChanges();
        tick();

        let startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        let codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        let cloneRepoButton = debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepoButton).toBeNull();

        exercise.allowOfflineIde = true;

        fixture.detectChanges();
        tick();

        startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        cloneRepoButton = debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepoButton).not.toBeNull();
    }));

    // Quiz not supported yet
    it.each([ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])(
        'should disable start exercise button before start date %s',
        fakeAsync((type: ExerciseType) => {
            comp.exercise = { type, releaseDate: dayjs().subtract(1, 'hour'), startDate: dayjs().add(1, 'hour') } as ProgrammingExercise;

            fixture.detectChanges();
            tick();

            const startExerciseButton = debugElement.query(By.css('button.start-exercise'));
            expect(startExerciseButton).not.toBeNull();
            expect(startExerciseButton.componentInstance.overwriteDisabled).toBeTrue();
        }),
    );

    describe('onInit', () => {
        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: false }, { started: true }] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, false],
            [{ type: ExerciseType.TEXT } as TextExercise, undefined],
        ])('should determine if it is an uninitialized quiz', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.uninitializedQuiz).toBe(expected);
        });

        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [] as StudentParticipation[] } as QuizExercise, false],
            [
                { type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [{ initializationState: InitializationState.UNINITIALIZED }] } as QuizExercise,
                false,
            ],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [{ initializationState: InitializationState.FINISHED }] } as QuizExercise, false],
        ])('should determine if quiz is not started', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.quizNotStarted).toBe(expected);
        });
    });

    it('ngOnChanges should determine participations', () => {
        const gradedParticipation = { id: 42 };
        const practiceParticipation = { id: 43, testRun: true };

        comp.exercise = { studentParticipations: [gradedParticipation, practiceParticipation] } as Exercise;
        comp.ngOnChanges();
        expect(comp.gradedParticipation).toEqual(gradedParticipation);
        expect(comp.practiceParticipation).toEqual(practiceParticipation);
    });

    it.each([
        [
            { studentParticipations: [{ initializationState: InitializationState.INITIALIZED }], type: ExerciseType.TEXT, dueDate: dayjs().add(1, 'day') } as Exercise,
            true,
            'openTextEditor',
            false,
        ],
        [
            { studentParticipations: [{ initializationState: InitializationState.INITIALIZED }], type: ExerciseType.TEXT, dueDate: dayjs().subtract(1, 'day') } as Exercise,
            false,
            undefined,
            undefined,
        ],
        [
            { studentParticipations: [{ initializationState: InitializationState.INITIALIZED }], type: ExerciseType.TEXT, dueDate: undefined } as Exercise,
            true,
            'openTextEditor',
            false,
        ],
        [
            { studentParticipations: [{ initializationState: InitializationState.FINISHED }], type: ExerciseType.TEXT, dueDate: dayjs().add(1, 'day') } as Exercise,
            true,
            'openTextEditor',
            false,
        ],
        [
            { studentParticipations: [{ initializationState: InitializationState.FINISHED }], type: ExerciseType.TEXT, dueDate: dayjs().subtract(1, 'day') } as Exercise,
            true,
            'viewSubmissions',
            true,
        ],
        [
            { studentParticipations: [{ initializationState: InitializationState.FINISHED }], type: ExerciseType.TEXT, dueDate: undefined } as Exercise,
            true,
            'openTextEditor',
            false,
        ],
        [
            {
                studentParticipations: [{ initializationState: InitializationState.FINISHED, results: [{ rated: true }] }],
                type: ExerciseType.TEXT,
                dueDate: dayjs().subtract(1, 'day'),
            } as Exercise,
            true,
            'viewResults',
            true,
        ],
        [
            { studentParticipations: [{ initializationState: InitializationState.FINISHED, results: [{ rated: true }] }], type: ExerciseType.TEXT, dueDate: undefined } as Exercise,
            true,
            'viewResults',
            true,
        ],
    ])(
        'should show correct open exercise button for text exercises',
        fakeAsync((exercise: Exercise, shouldShowButton: boolean, expectedLabel: string | undefined, shouldBeOutlined: boolean | undefined) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            comp.ngOnChanges();

            fixture.detectChanges();
            tick();

            const button = debugElement.query(By.css('button.open-exercise'));

            if (shouldShowButton) {
                expect(button).not.toBeNull();
                expect(button.componentInstance.buttonLabel).toBe('artemisApp.exerciseActions.' + expectedLabel);
                expect(button.componentInstance.outlined).toBe(shouldBeOutlined);
            } else {
                expect(button).toBeNull();
            }
        }),
    );
});
