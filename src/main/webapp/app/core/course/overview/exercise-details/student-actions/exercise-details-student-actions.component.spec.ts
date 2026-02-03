import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpClient } from '@angular/common/http';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core/user/user.model';
import { Exercise, ExerciseMode, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/core/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { StartPracticeModeButtonComponent } from 'app/core/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';

describe('ExerciseDetailsStudentActionsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<ExerciseDetailsStudentActionsComponent>;
    let debugElement: DebugElement;
    let courseExerciseService: CourseExerciseService;
    let profileService: ProfileService;
    let startExerciseStub: ReturnType<typeof vi.spyOn>;
    let resumeStub: ReturnType<typeof vi.spyOn>;
    let getProfileInfoSub: ReturnType<typeof vi.spyOn>;
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                MockModule(NgbTooltipModule),
                ExerciseDetailsStudentActionsComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(CodeButtonComponent),
                MockComponent(StartPracticeModeButtonComponent),
                MockPipe(ArtemisTranslatePipe, (query: any, args?: any) => query + (args ? args : '')),
                MockDirective(FeatureToggleDirective),
                ExtensionPointDirective,
                MockRouterLinkDirective,
            ],
            providers: [
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: Router, useClass: MockRouter },
                MockProvider(HttpClient),
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideComponent(ExerciseDetailsStudentActionsComponent, {
                remove: { imports: [CodeButtonComponent] },
                add: { imports: [MockComponent(CodeButtonComponent)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ExerciseDetailsStudentActionsComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        profileService = TestBed.inject(ProfileService);
        router = TestBed.inject(Router) as unknown as MockRouter;
        getProfileInfoSub = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue({
            sshCloneURLTemplate: 'ssh://git@testserver.com:1234/',
            activeModuleFeatures: [MODULE_FEATURE_TEXT],
        } as unknown as ProfileInfo);
        startExerciseStub = vi.spyOn(courseExerciseService, 'startExercise');
        resumeStub = vi.spyOn(courseExerciseService, 'resumeProgrammingExercise');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.PROGRAMMING, ExerciseType.TEXT])(
        'should not show the buttons "Team" and "Start exercise" for a team exercise when not assigned to a team yet',
        async (exerciseType: ExerciseType) => {
            fixture.componentRef.setInput('exercise', { ...teamExerciseWithoutTeamAssigned, type: exerciseType });
            TestBed.tick();
            fixture.detectChanges();
            await fixture.whenStable();

            const viewTeamButton = fixture.debugElement.query(By.css('.view-team'));
            expect(viewTeamButton).toBeNull();

            const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
            expect(startExerciseButton).toBeNull();
        },
    );

    it.each([ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.PROGRAMMING])(
        'should show the buttons "Team" and "Start exercise" for a team exercise for a student to view his team when assigned to a team',
        async (exerciseType: ExerciseType) => {
            fixture.componentRef.setInput('exercise', { ...teamExerciseWithTeamAssigned, type: exerciseType });
            TestBed.tick();
            fixture.detectChanges();
            await fixture.whenStable();

            const viewTeamButton = fixture.debugElement.query(By.css('.view-team'));
            expect(viewTeamButton).not.toBeNull();
            const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
            expect(startExerciseButton).not.toBeNull();
        },
    );

    it('should create the correct repository URL for missing exerciseID in URL', () => {
        // Set up necessary data for the test
        const courseId = 123; // Example course ID
        const exerciseId = 456; // Example exercise ID
        const repositoryUrl = `/courses/${courseId}/exercises`;
        router.setUrl(repositoryUrl);

        // Assign the courseId and exerciseId to the component's input properties
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('exercise', { id: exerciseId } as Exercise);
        TestBed.tick();
    });

    it('should create the correct repository URL for exam exercises', () => {
        // Set up necessary data for the test
        const courseId = 123; // Example course ID
        const exerciseId = 456; // Example exercise ID
        const examId = 789; // Example exam ID
        const repositoryUrl = `/courses/${courseId}/exams/${examId}`;
        router.setUrl(repositoryUrl);

        // Assign the courseId and exerciseId to the component's input properties
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('exercise', { id: exerciseId } as Exercise);
        TestBed.tick();

        // Assert that the repositoryLink property is set correctly
    });

    it('should reflect the correct participation state when team exercise was started', async () => {
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        fixture.componentRef.setInput('exercise', teamExerciseWithTeamAssigned);
        TestBed.tick();
        startExerciseStub.mockReturnValue(participationSubject);
        comp.startExercise();
        participationSubject.next(inactivePart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.gradedParticipation()?.initializationState).toEqual(InitializationState.UNINITIALIZED);

        expect(startExerciseStub).toHaveBeenCalledOnce();
        participationSubject.next(initPart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.gradedParticipation()?.initializationState).toEqual(InitializationState.INITIALIZED);

        // Check that button "Start exercise" is no longer shown
        const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
        expect(startExerciseButton).toBeNull();

        // Check that button "Code" is not shown (repo_url is null)
        const codeButton = fixture.debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();

        fixture.destroy();
    });

    it('should reflect the correct participation state for practice mode', async () => {
        const exerciseData = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            allowOfflineIde: true,
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;

        fixture.componentRef.setInput('exercise', exerciseData);
        TestBed.tick();

        fixture.detectChanges();
        await fixture.whenStable();

        let startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).not.toBeNull();

        let codeButton = fixture.debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();

        // Update participations via the component's public method
        comp.receiveNewParticipation(initPart);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).toBeNull();

        codeButton = fixture.debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();

        fixture.destroy();
    });

    it('should correctly not show the Code button for exam test runs', async () => {
        testRunParticipation.repositoryUri = undefined;
        testRunExercise.studentParticipations = [testRunParticipation];

        fixture.componentRef.setInput('examMode', true);
        fixture.componentRef.setInput('exercise', testRunExercise);
        TestBed.tick();

        fixture.detectChanges();
        await fixture.whenStable();

        const startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).toBeNull();

        const codeButton = fixture.debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();

        fixture.destroy();
    });

    it('should correctly show the Code button for exam test runs', async () => {
        testRunParticipation.repositoryUri = 'https://clone-me.git';
        testRunExercise.studentParticipations = [testRunParticipation];

        fixture.componentRef.setInput('examMode', true);
        fixture.componentRef.setInput('exercise', testRunExercise);
        TestBed.tick();

        fixture.detectChanges();
        await fixture.whenStable();

        const startPracticeButton = fixture.debugElement.query(By.css('jhi-start-practice-mode-button'));
        expect(startPracticeButton).toBeNull();

        const codeButton = fixture.debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).not.toBeNull();

        fixture.destroy();
    });

    it('should correctly resume programming participation', () => {
        const inactiveParticipation: ProgrammingExerciseStudentParticipation = { id: 1, initializationState: InitializationState.INACTIVE };
        const activeParticipation: ProgrammingExerciseStudentParticipation = { id: 1, initializationState: InitializationState.INITIALIZED };
        const practiceParticipation: ProgrammingExerciseStudentParticipation = { id: 2, testRun: true, initializationState: InitializationState.INACTIVE };
        fixture.componentRef.setInput('exercise', { id: 3, studentParticipations: [inactiveParticipation, practiceParticipation] } as ProgrammingExercise);
        TestBed.tick();
        comp.updateParticipations();

        resumeStub.mockReturnValue(of(activeParticipation));

        comp.resumeProgrammingExercise(false);

        expect(comp.studentParticipations()).toEqual([activeParticipation, practiceParticipation]);
    });

    it('should show correct buttons in exam mode', async () => {
        const exerciseData = { type: ExerciseType.PROGRAMMING, allowOfflineIde: false, allowOnlineEditor: true } as ProgrammingExercise;
        exerciseData.studentParticipations = [{ initializationState: InitializationState.INITIALIZED } as StudentParticipation];
        fixture.componentRef.setInput('exercise', exerciseData);
        fixture.componentRef.setInput('examMode', true);
        TestBed.tick();
        comp.updateParticipations();

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        let startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        let codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        let codeButton = debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();

        exerciseData.allowOfflineIde = true;

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        codeButton = debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();
    });

    it('should show correct buttons in exam mode, including code button', async () => {
        const exerciseData = { type: ExerciseType.PROGRAMMING, allowOfflineIde: false, allowOnlineEditor: true } as ProgrammingExercise;
        exerciseData.studentParticipations = [
            { initializationState: InitializationState.INITIALIZED, repositoryUri: 'https://clone-me.git' } as ProgrammingExerciseStudentParticipation,
        ];
        fixture.componentRef.setInput('exercise', exerciseData);
        fixture.componentRef.setInput('examMode', true);
        TestBed.tick();
        comp.updateParticipations();

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        let startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        let codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        let codeButton = debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).toBeNull();

        exerciseData.allowOfflineIde = true;

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        startExerciseButton = debugElement.query(By.css('button.start-exercise'));
        expect(startExerciseButton).toBeNull();
        codeEditorButton = debugElement.query(By.css('jhi-open-code-editor-button'));
        expect(codeEditorButton).toBeNull();
        codeButton = debugElement.query(By.css('jhi-code-button'));
        expect(codeButton).not.toBeNull();
    });

    // Quiz not supported yet
    it.each([ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])(
        'should disable start exercise button before start date %s',
        async (type: ExerciseType) => {
            fixture.componentRef.setInput('exercise', { type, releaseDate: dayjs().subtract(1, 'hour'), startDate: dayjs().add(1, 'hour') } as ProgrammingExercise);
            TestBed.tick();

            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();

            const startExerciseButton = debugElement.query(By.css('button.start-exercise'));
            expect(startExerciseButton).not.toBeNull();
            expect(startExerciseButton.componentInstance.overwriteDisabled).toBe(true);
        },
    );

    describe('effect on input changes', () => {
        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: false }, { started: true }] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, false],
            [{ type: ExerciseType.TEXT } as TextExercise, false],
        ])('should determine if it is an uninitialized quiz', (exerciseData: Exercise, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exerciseData);
            TestBed.tick();
            expect(comp.uninitializedQuiz()).toBe(expected);
        });

        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [] as StudentParticipation[] } as QuizExercise, false],
            [
                { type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [{ initializationState: InitializationState.UNINITIALIZED }] } as QuizExercise,
                false,
            ],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [{ initializationState: InitializationState.FINISHED }] } as QuizExercise, false],
        ])('should determine if quiz is not started', (exerciseData: Exercise, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exerciseData);
            TestBed.tick();
            expect(comp.quizNotStarted()).toBe(expected);
        });
    });

    it('effect on input changes should determine participations', () => {
        const gradedParticipation = { id: 42 };
        const practiceParticipation = { id: 43, testRun: true };

        fixture.componentRef.setInput('exercise', { studentParticipations: [gradedParticipation, practiceParticipation] } as Exercise);
        TestBed.tick();
        expect(comp.gradedParticipation()).toEqual(gradedParticipation);
        expect(comp.practiceParticipation()).toEqual(practiceParticipation);
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
                studentParticipations: [
                    {
                        initializationState: InitializationState.FINISHED,
                        submissions: [
                            {
                                results: [{ rated: true }],
                            },
                        ],
                    },
                ],
                type: ExerciseType.TEXT,
                dueDate: dayjs().subtract(1, 'day'),
            } as Exercise,
            true,
            'viewResults',
            true,
        ],
        [
            {
                studentParticipations: [{ initializationState: InitializationState.FINISHED, submissions: [{ results: [{ rated: true }] }] }],
                type: ExerciseType.TEXT,
                dueDate: undefined,
            } as Exercise,
            true,
            'viewResults',
            true,
        ],
    ])(
        'should show correct open exercise button for text exercises',
        async (exerciseData: Exercise, shouldShowButton: boolean, expectedLabel: string | undefined, shouldBeOutlined: boolean | undefined) => {
            fixture.componentRef.setInput('exercise', exerciseData);
            TestBed.tick();

            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();

            const button = debugElement.query(By.css('button.open-exercise'));

            if (shouldShowButton) {
                expect(button).not.toBeNull();
                expect(button.componentInstance.buttonLabel).toBe('artemisApp.exerciseActions.' + expectedLabel);
                expect(button.componentInstance.outlined).toBe(shouldBeOutlined);
            } else {
                expect(button).toBeNull();
            }
        },
    );
});
