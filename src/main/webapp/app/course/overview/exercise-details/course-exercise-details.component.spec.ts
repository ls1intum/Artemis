import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Navigation, ParamMap, Router, UrlTree, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState, Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { LiveQuizParticipationStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TeamService } from 'app/exercise/team/team.service';
import { CourseExerciseDetailsComponent } from 'app/course/overview/exercise-details/course-exercise-details.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, NEVER, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExtensionPointDirective } from 'app/foundation/extension-point/extension-point.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { LtiInitializerComponent } from 'app/course/overview/exercise-details/lti-initializer/lti-initializer.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { LockRepositoryPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { ProblemStatementComponent } from 'app/course/overview/exercise-details/problem-statement/problem-statement.component';
import { ExerciseInfoComponent } from 'app/exercise/exercise-info/exercise-info.component';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ScienceService } from 'app/foundation/science/science.service';

import { mockCourseSettings } from 'test/helpers/mocks/iris/mock-settings';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { ElementRef, signal } from '@angular/core';
import { ResetRepoButtonComponent } from 'app/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('CourseExerciseDetailsComponent', () => {
    let comp: CourseExerciseDetailsComponent;
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;
    let exerciseService: ExerciseService;
    let teamService: TeamService;
    let participationService: ParticipationService;
    let participationWebsocketService: ParticipationWebsocketService;
    let complaintService: ComplaintService;
    let getExerciseDetailsMock: ReturnType<typeof vi.spyOn>;
    let mergeStudentParticipationMock: ReturnType<typeof vi.spyOn>;
    let subscribeForParticipationChangesMock: ReturnType<typeof vi.spyOn>;
    let participationWebsocketBehaviorSubject: BehaviorSubject<Participation | undefined>;
    let scienceService: ScienceService;
    let logEventStub: ReturnType<typeof vi.spyOn>;

    const exercise = {
        id: 42,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        course: {
            id: 1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        },
    } as unknown as Exercise;

    const textExercise = {
        id: 24,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        exampleSolution: 'Example<br>Solution',
    } as unknown as TextExercise;

    const plagiarismCaseInfo = { id: 20, verdict: PlagiarismVerdict.WARNING };

    const submissionPolicy = new LockRepositoryPolicy();

    const programmingExercise = {
        id: exercise.id,
        type: ExerciseType.PROGRAMMING,
        studentParticipations: [],
        course: { id: 2 },
        allowComplaintsForAutomaticAssessments: true,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: true,
        numberOfAssessmentsOfCorrectionRounds: [],
        submissionPolicy: submissionPolicy,
    } as ProgrammingExercise;

    const parentParams = { courseId: 1 };
    const parentRoute = { parent: { params: of(parentParams) } } as any as ActivatedRoute;
    const route = {
        params: of({ exerciseId: exercise.id }),
        parent: parentRoute,
        queryParams: of({ welcome: '' }),
    } as any as ActivatedRoute;

    MockInstance(DiscussionSectionComponent, 'content', signal(new ElementRef(document.createElement('div'))));
    MockInstance(DiscussionSectionComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
    // @ts-ignore
    MockInstance(DiscussionSectionComponent, 'postCreateEditModal', signal(new ElementRef(document.createElement('div'))));

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                CourseExerciseDetailsComponent,
                MockComponent(DiscussionSectionComponent),
                FaIconComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockDirective(MarkdownDirective),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ResultComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent),
                MockComponent(ProblemStatementComponent),
                MockComponent(ResetRepoButtonComponent),
                MockComponent(RatingComponent),
                MockRouterLinkDirective,
                MockDirective(ExtensionPointDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(LtiInitializerComponent),
                MockComponent(ModelingEditorComponent),
                MockComponent(ExerciseInfoComponent),
                MockComponent(ExerciseHeadersInformationComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ScienceService, useClass: MockScienceService },
                MockProvider(ExerciseService),
                MockProvider(ParticipationService),
                MockProvider(TeamService),
                MockProvider(QuizExerciseService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(ComplaintService),
                MockProvider(SubmissionPolicyService),
                MockProvider(PlagiarismCasesService),
                MockProvider(AlertService),
                MockProvider(IrisSettingsService),
                MockProvider(DialogService),
                { provide: MetisConversationService, useClass: MockMetisConversationService },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
        comp = fixture.componentInstance;

        comp.studentParticipations = [];

        // mock exerciseService
        exerciseService = TestBed.inject(ExerciseService);
        getExerciseDetailsMock = vi.spyOn(exerciseService, 'getExerciseDetails');
        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: exercise } }));

        // mock teamService, needed for team assignment
        teamService = TestBed.inject(TeamService);
        const teamAssignmentPayload = {
            exerciseId: 2,
            teamId: 2,
            studentParticipations: [],
        } as TeamAssignmentPayload;
        vi.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockReturnValue(Promise.resolve(of(teamAssignmentPayload)));

        // mock participationService, needed for team assignment
        participationWebsocketBehaviorSubject = new BehaviorSubject<Participation | undefined>(undefined);
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
        subscribeForParticipationChangesMock = vi.spyOn(participationWebsocketService, 'subscribeForParticipationChanges');
        subscribeForParticipationChangesMock.mockReturnValue(participationWebsocketBehaviorSubject);

        complaintService = TestBed.inject(ComplaintService);

        scienceService = TestBed.inject(ScienceService);
        logEventStub = vi.spyOn(scienceService, 'logEvent');

        participationService = TestBed.inject(ParticipationService);
        mergeStudentParticipationMock = vi.spyOn(participationService, 'mergeStudentParticipations');
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should initialize', async () => {
        vi.useFakeTimers();
        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(500);
        expect(comp.exerciseId).toBe(42);
        expect(comp.courseId).toBe(1);
        expect(comp.exercise).toStrictEqual(exercise);
        expect(comp.showMoreResults()).toBe(false);
        comp.ngOnDestroy();
    });

    it('should have student participations', async () => {
        vi.useFakeTimers();
        const studentParticipation = new StudentParticipation();
        studentParticipation.student = new User(99);
        studentParticipation.testRun = false;
        const result = new Result();
        result.id = 1;
        result.completionDate = dayjs();
        const submission = new TextSubmission();
        submission.results = [result];
        submission.participation = studentParticipation;
        studentParticipation.submissions = [submission];
        studentParticipation.type = ParticipationType.STUDENT;
        studentParticipation.id = 42;

        studentParticipation.exercise = exercise;

        const exerciseDetail = { exercise: { ...exercise, studentParticipations: [studentParticipation] }, plagiarismCaseInfo: plagiarismCaseInfo };
        const exerciseDetailResponse = of({ body: exerciseDetail });

        // return initial participation for websocketService
        vi.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([studentParticipation]);
        vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({} as EntityResponseType));

        // mock participationService methods
        mergeStudentParticipationMock.mockReturnValue([studentParticipation]);
        const getSpecificMock = vi.spyOn(participationService, 'getSpecificStudentParticipation');
        getSpecificMock.mockImplementation((participations, testRun) => {
            return participations?.find((p) => p.testRun === testRun);
        });

        const changedParticipation = deepClone(studentParticipation);
        const changedResult = { ...result, id: 2 };

        changedParticipation.submissions![0].results = [changedResult];
        subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(changedParticipation));

        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(500);
        await fixture.whenStable();

        // override mock to return exercise with participation
        getExerciseDetailsMock.mockReturnValue(exerciseDetailResponse);
        mergeStudentParticipationMock.mockReturnValue([changedParticipation]);
        comp.loadExercise();
        await vi.advanceTimersByTimeAsync(0);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.courseId).toBe(1);
        expect(comp.studentParticipations?.[0].exercise?.id).toBe(exercise.id);
        expect(comp.exercise!.id).toBe(exercise.id);
        expect(comp.studentParticipations[0].submissions![0].results![0]).toStrictEqual(changedResult);
        expect(comp.plagiarismCaseInfo()).toEqual(plagiarismCaseInfo);
        expect(comp.showMoreResults()).toBe(false);
        expect(comp.exerciseRatedBadge(result)).toBe('bg-info');
    });

    it('should not be a quiz exercise', () => {
        comp.exercise = { ...exercise };
        expect(comp.quizExerciseStatus).toBeUndefined();
    });

    it('should configure example solution for exercise', () => {
        const exampleSolutionInfo = {} as ExampleSolutionInfo;
        const exerciseServiceSpy = vi.spyOn(ExerciseService, 'extractExampleSolutionInfo').mockReturnValue(exampleSolutionInfo);

        const artemisMarkdown = TestBed.inject(ArtemisMarkdownService);

        expect(comp.exampleSolutionInfo()).toBeUndefined();
        const newExercise = { ...textExercise };
        comp.showIfExampleSolutionPresent(newExercise);
        expect(comp.exampleSolutionInfo()).toBe(exampleSolutionInfo);
        expect(exerciseServiceSpy).toHaveBeenCalledOnce();
        expect(exerciseServiceSpy).toHaveBeenCalledWith(newExercise, artemisMarkdown);
    });

    it('should sort results by completion date in ascending order', () => {
        const result1 = { completionDate: dayjs().subtract(2, 'days') } as Result;
        const result2 = { completionDate: dayjs().subtract(1, 'day') } as Result;
        const result3 = { completionDate: dayjs() } as Result;

        const results = [result3, result1, result2];
        results.sort((a, b) => comp['resultSortFunction'](a, b));

        expect(results).toEqual([result1, result2, result3]);
    });

    it('should handle results with undefined completion dates', () => {
        const result1 = { completionDate: dayjs().subtract(2, 'days') } as Result;
        const result2 = { completionDate: undefined } as Result;
        const result3 = { completionDate: dayjs() } as Result;

        const results = [result3, result1, result2];
        results.sort((a, b) => comp['resultSortFunction'](a, b));

        expect(results).toEqual([result1, result3, result2]);
    });

    it('should handle empty results array', () => {
        const results: Result[] = [];
        results.sort((a, b) => comp['resultSortFunction'](a, b));

        expect(results).toEqual([]);
    });

    it('should handle results with same completion dates', () => {
        const date = dayjs();
        const result1 = { completionDate: date } as Result;
        const result2 = { completionDate: date } as Result;

        const results = [result2, result1];
        results.sort((a, b) => comp['resultSortFunction'](a, b));

        expect(results).toEqual([result2, result1]);
    });

    describe('live quiz status seeding', () => {
        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: false }] }, LiveQuizParticipationStatus.NOT_STARTED],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }] }, LiveQuizParticipationStatus.PARTICIPATING],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], quizEnded: true }, LiveQuizParticipationStatus.MISSED],
            [{ type: ExerciseType.PROGRAMMING }, undefined],
        ])('should seed the live quiz status from the loaded exercise', (exerciseData, expected) => {
            vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue(undefined);

            comp.handleNewExercise({ exercise: exerciseData as unknown as Exercise });

            expect(comp.liveQuizStatus()).toBe(expected);
        });

        it('should seed SUBMITTED when the graded participation has a submitted submission', () => {
            vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue({ submissions: [{ submitted: true }] } as StudentParticipation);

            comp.handleNewExercise({ exercise: { type: ExerciseType.QUIZ, quizBatches: [{ started: true }] } as unknown as Exercise });

            expect(comp.liveQuizStatus()).toBe(LiveQuizParticipationStatus.SUBMITTED);
        });

        it('should not override an ended quiz where the student submitted (results are shown instead)', () => {
            vi.spyOn(participationService, 'getSpecificStudentParticipation').mockReturnValue({ submissions: [{ submitted: true }] } as StudentParticipation);

            comp.handleNewExercise({ exercise: { type: ExerciseType.QUIZ, quizEnded: true } as unknown as Exercise });

            expect(comp.liveQuizStatus()).toBeUndefined();
        });
    });

    it('should handle new programming exercise', () => {
        const courseId = programmingExercise.course!.id!;

        comp.courseId = courseId;

        comp.handleNewExercise({ exercise: programmingExercise });
        expect(comp.baseResource()).toBe(`/course-management/${courseId}/${programmingExercise.type}-exercises/${programmingExercise.id}/`);
        expect(comp.allowComplaintsForAutomaticAssessments()).toBe(true);
        expect(comp.submissionPolicy()).toEqual(submissionPolicy);
    });

    it('should handle error when getting latest rated result', async () => {
        vi.useFakeTimers();
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        const error = { message: 'Error msg' };
        const complaintServiceSpy = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(throwError(() => error));

        const submissionId = 55;
        const gradedParticipation = { submissions: [{ id: submissionId }], testRun: false } as StudentParticipation;

        // Mock getSpecificStudentParticipation to return the graded participation
        vi.spyOn(participationService, 'getSpecificStudentParticipation').mockImplementation((participations, testRun) => {
            return participations?.find((p) => p.testRun === testRun);
        });

        comp.studentParticipations = [gradedParticipation];
        comp.sortedHistoryResults = [{ id: 2 }];
        comp.exercise = { ...exercise };

        comp.loadComplaintAndLatestRatedResult();
        await vi.advanceTimersByTimeAsync(0);

        expect(complaintServiceSpy).toHaveBeenCalledOnce();
        expect(complaintServiceSpy).toHaveBeenCalledWith(submissionId);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith(error.message);
    });

    it('should handle participation update', async () => {
        vi.useFakeTimers();
        const submissionId = 55;
        const submission: Submission = { id: submissionId } satisfies Submission;
        const participation = { submissions: [submission], testRun: false } as StudentParticipation;
        comp.studentParticipations = [participation];
        comp.sortedHistoryResults = [{ id: 2 }];
        comp.exercise = { ...programmingExercise };

        comp.courseId = programmingExercise.course!.id!;

        comp.handleNewExercise({ exercise: programmingExercise });
        await vi.advanceTimersByTimeAsync(0);

        const newParticipation = { ...participation, submissions: [submission, { id: submissionId + 1 } satisfies Submission] } satisfies Participation;

        mergeStudentParticipationMock.mockReturnValue([newParticipation]);

        participationWebsocketBehaviorSubject.next({ ...newParticipation, exercise: programmingExercise });
    });

    it.each<[string[]]>([[[]], [[MODULE_FEATURE_IRIS]]])('should load iris settings only if module feature iris is active', async (activeModuleFeatures: string[]) => {
        vi.useFakeTimers();
        // Setup
        const submissionPolicy = new LockRepositoryPolicy();
        const programmingExercise = {
            id: 42,
            type: ExerciseType.PROGRAMMING,
            studentParticipations: [],
            course: { id: 1 },
            submissionPolicy: submissionPolicy,
        } as unknown as ProgrammingExercise;

        const fakeSettings = mockCourseSettings(1, true);

        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: programmingExercise } }));

        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures } as any as ProfileInfo);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(activeModuleFeatures.includes(MODULE_FEATURE_IRIS));

        const irisSettingsService = TestBed.inject(IrisSettingsService);
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(fakeSettings));

        // Act
        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(0);

        if (activeModuleFeatures.includes(MODULE_FEATURE_IRIS)) {
            // Should have called getCourseSettings if 'iris' is active
            expect(getCourseSettingsSpy).toHaveBeenCalledWith(1);
            expect(comp.irisEnabled()).toBe(true);
            expect(comp.irisChatEnabled()).toBe(true);
        } else {
            // Should not have called getCourseSettings if 'iris' is not active
            expect(getCourseSettingsSpy).not.toHaveBeenCalled();
            expect(comp.irisEnabled()).toBe(false);
            expect(comp.irisChatEnabled()).toBe(false);
        }
    });

    it('should load iris settings for text exercise when Iris module feature is active', async () => {
        vi.useFakeTimers();
        const textExerciseWithCourse = {
            id: 42,
            type: ExerciseType.TEXT,
            studentParticipations: [],
            course: { id: 1 },
        } as unknown as TextExercise;

        const fakeSettings = mockCourseSettings(1, true);

        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: textExerciseWithCourse } }));

        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        const irisSettingsService = TestBed.inject(IrisSettingsService);
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(fakeSettings));

        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(0);

        expect(getCourseSettingsSpy).toHaveBeenCalledWith(1);
        expect(comp.irisEnabled()).toBe(true);
        expect(comp.irisChatEnabled()).toBe(true);
    });

    it('should not load iris settings when exercise is in an exam group', async () => {
        vi.useFakeTimers();
        const examExercise = {
            id: 42,
            type: ExerciseType.TEXT,
            studentParticipations: [],
            course: { id: 1 },
            exerciseGroup: { id: 10 },
        } as unknown as TextExercise;

        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: examExercise } }));

        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        const irisSettingsService = TestBed.inject(IrisSettingsService);
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit');

        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(0);

        expect(getCourseSettingsSpy).not.toHaveBeenCalled();
        expect(comp.irisEnabled()).toBe(false);
        expect(comp.irisChatEnabled()).toBe(false);
    });

    it('should log event on init', () => {
        fixture.detectChanges();
        expect(logEventStub).toHaveBeenCalledExactlyOnceWith(ScienceEventType.EXERCISE__OPEN, exercise.id);
    });

    it('should not show discussion section when communication is disabled', async () => {
        const newExercise = {
            ...exercise,
            course: { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED },
        };
        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: newExercise } }));

        fixture.detectChanges();
        await fixture.whenStable();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeFalsy();
    });

    it('should show discussion section when communication is enabled', async () => {
        vi.useFakeTimers();
        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(500);

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeTruthy();
    });

    it('should propagate a newly started participation into the cached course so the sidebar updates live', () => {
        const courseStorageService = TestBed.inject(CourseStorageService);
        const cachedExercise = { id: exercise.id, studentParticipations: [] } as unknown as Exercise;
        const cachedCourse = { id: 1, exercises: [cachedExercise] } as unknown as Course;
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(cachedCourse);
        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse').mockImplementation(() => {});

        comp.courseId = 1;
        comp.exercise = { ...exercise, studentParticipations: [] } as Exercise;
        const newParticipation = { id: 777 } as StudentParticipation;

        comp.onNewParticipation(newParticipation);

        expect(updateCourseSpy).toHaveBeenCalledWith(cachedCourse);
        expect(cachedExercise.studentParticipations).toContain(newParticipation);
    });

    it('should propagate an already-present participation into the cached course (start navigates to the code editor, re-resolving it)', () => {
        // Starting a programming exercise navigates to the code editor, which re-resolves this component with the
        // participation already loaded into _studentParticipations. onNewParticipation then takes the "already present"
        // branch, so the cached-course propagation must still run — otherwise the sidebar card stays at "Not yet started".
        const courseStorageService = TestBed.inject(CourseStorageService);
        const existingParticipation = { id: 778 } as StudentParticipation;
        const cachedExercise = { id: exercise.id, studentParticipations: [] } as unknown as Exercise;
        const cachedCourse = { id: 1, exercises: [cachedExercise] } as unknown as Course;
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(cachedCourse);
        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse').mockImplementation(() => {});

        comp.courseId = 1;
        comp.exercise = { ...exercise, studentParticipations: [existingParticipation] } as Exercise;
        // The participation is already resolved on this component instance before onNewParticipation fires.
        (comp as unknown as { _studentParticipations: { set: (value: StudentParticipation[]) => void } })._studentParticipations.set([existingParticipation]);

        comp.onNewParticipation(existingParticipation);

        expect(updateCourseSpy).toHaveBeenCalledWith(cachedCourse);
        // onNewParticipation now merges submissions into a fresh participation object (so prior attempts survive and
        // the signal change is detected), so assert by id rather than reference identity.
        expect(cachedExercise.studentParticipations?.some((p) => p.id === existingParticipation.id)).toBe(true);
    });

    it('should preserve prior attempts when onNewParticipation receives a payload carrying only the latest submission', () => {
        // Regression test for #12955 / #12972 (bug 1): a practice submit emits a participation that only carries the
        // latest submission. Replacing the stored participation wholesale dropped every prior attempt from the
        // result-history dropdown until a page refresh. onNewParticipation must merge submissions instead.
        comp.courseId = 1;
        comp.exercise = { ...exercise, studentParticipations: [] } as Exercise;
        const existingParticipation = { id: 555, submissions: [{ id: 1 } as Submission, { id: 2 } as Submission] } as StudentParticipation;
        comp.studentParticipations = [existingParticipation];

        const incomingParticipation = { id: 555, submissions: [{ id: 3 } as Submission] } as StudentParticipation;
        comp.onNewParticipation(incomingParticipation);

        const merged = comp.studentParticipations.find((p) => p.id === 555);
        expect(merged?.submissions?.map((s) => s.id)).toEqual([1, 2, 3]);
    });

    it('should replace a re-sent submission in place without duplicating it (onNewParticipation merge)', () => {
        comp.courseId = 1;
        comp.exercise = { ...exercise, studentParticipations: [] } as Exercise;
        const existingParticipation = { id: 555, submissions: [{ id: 1, submitted: false } as Submission, { id: 2 } as Submission] } as StudentParticipation;
        comp.studentParticipations = [existingParticipation];

        // The incoming payload re-sends submission 1 with an updated field; it must replace, not append.
        const incomingParticipation = { id: 555, submissions: [{ id: 1, submitted: true } as Submission] } as StudentParticipation;
        comp.onNewParticipation(incomingParticipation);

        const merged = comp.studentParticipations.find((p) => p.id === 555);
        expect(merged?.submissions?.map((s) => s.id)).toEqual([1, 2]);
        expect(merged?.submissions?.find((s) => s.id === 1)?.submitted).toBe(true);
    });

    describe('mode of the participation named in the URL', () => {
        const gradedParticipation = { id: 679, testRun: false } as StudentParticipation;
        const practiceParticipation = { id: 680, testRun: true } as StudentParticipation;

        /** The part of the route the component reads, so a change to the stub's shape stays type checked. */
        type RouteWithParticipationChild = { firstChild?: { snapshot: { paramMap: ParamMap } } };

        /** Puts a child route carrying `participationId` under the exercise route, as the embedded editor does. */
        function routeToParticipation(participationId: string | undefined) {
            (route as unknown as RouteWithParticipationChild).firstChild = participationId ? { snapshot: { paramMap: convertToParamMap({ participationId }) } } : undefined;
        }

        beforeEach(() => {
            getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: { ...exercise, studentParticipations: [gradedParticipation, practiceParticipation] } } }));
            mergeStudentParticipationMock.mockReturnValue([gradedParticipation, practiceParticipation]);
            // ParticipationService is mocked for this spec, so the graded/practice split has to behave like the real one.
            vi.spyOn(participationService, 'getSpecificStudentParticipation').mockImplementation((participations, testRun) =>
                (participations ?? []).find((participation) => !!participation.testRun === testRun),
            );
        });

        // `route` is shared across the whole spec, unlike the router, which `useClass: MockRouter` re-creates per test.
        afterEach(() => {
            routeToParticipation(undefined);
        });

        it('selects the practice mode when the URL addresses the practice participation', async () => {
            // Reproduces the reload after starting the practice mode: without this the mode fell back to graded and the
            // split panel redirected the editor to the graded participation, whose repository is read-only after the
            // due date.
            routeToParticipation('680');

            comp.loadExercise();

            expect(comp.participationMode()).toBe('practice');
        });

        it('selects the practice mode before the details response arrives', async () => {
            // The split panel routes the embedded editor on the first change detection, so a mode that only became
            // practice once the response landed arrived after that redirect had already put the graded participation
            // back into the URL. The locally known participations have to be enough.
            getExerciseDetailsMock.mockReturnValue(NEVER);
            vi.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([gradedParticipation, practiceParticipation]);
            routeToParticipation('680');

            comp.loadExercise();

            expect(comp.participationMode()).toBe('practice');
        });

        it('selects the practice mode from the running navigation while the child route is not activated yet', async () => {
            // The component is created during the navigation to the editor, when `router.url` still holds the URL being
            // left. Only the navigation in flight names the participation the student is going to.
            getExerciseDetailsMock.mockReturnValue(NEVER);
            vi.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([gradedParticipation, practiceParticipation]);
            const router = TestBed.inject(Router) as unknown as MockRouter;
            router.setUrl('/courses/1/exercises/2');
            // Typed against the real `Navigation`, so a rename of the field the component reads breaks the test.
            const inFlightNavigation: Pick<Navigation, 'finalUrl'> = {
                finalUrl: { toString: () => '/courses/1/exercises/programming-exercises/2/code-editor/680' } as UrlTree,
            };
            router.currentNavigation.mockReturnValue(inFlightNavigation);

            comp.loadExercise();

            expect(comp.participationMode()).toBe('practice');
        });

        it('selects the practice mode from the URL while the child route is not activated yet', async () => {
            // Angular activates the editor's child route only after this component has initialised, so on the first
            // pass the participation is named by the URL alone. Held before the response lands, because that is where
            // the mode has to be settled: the split panel routes the editor on the first change detection.
            getExerciseDetailsMock.mockReturnValue(NEVER);
            vi.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([gradedParticipation, practiceParticipation]);
            (TestBed.inject(Router) as unknown as MockRouter).setUrl('/courses/1/exercises/programming-exercises/2/code-editor/680');

            comp.loadExercise();

            expect(comp.participationMode()).toBe('practice');
        });

        it('keeps the graded mode when the URL addresses the graded participation', async () => {
            routeToParticipation('679');

            comp.loadExercise();

            expect(comp.participationMode()).toBe('graded');
        });

        it('follows the routed participation on a navigation that does not reload the exercise', async () => {
            // Switching the mode and going back changes only the child participation, so the exercise is not reloaded
            // and the navigation is the only signal. The mode has to follow the URL both ways, or it goes on describing
            // a participation the editor no longer shows.
            fixture.detectChanges();
            routeToParticipation('680');
            comp.loadExercise();
            expect(comp.participationMode()).toBe('practice');

            routeToParticipation('679');
            (TestBed.inject(Router) as unknown as MockRouter).setUrl('/courses/1/exercises/programming-exercises/2/code-editor/679');

            expect(comp.participationMode()).toBe('graded');
        });

        it('keeps the graded mode when no participation is addressed', async () => {
            comp.loadExercise();

            expect(comp.participationMode()).toBe('graded');
        });
    });

    it('should switch participationMode to practice for a test-run participation', () => {
        comp.courseId = 1;
        comp.exercise = { ...exercise, studentParticipations: [] } as Exercise;
        comp.studentParticipations = [];

        comp.onNewParticipation({ id: 999, testRun: true } as StudentParticipation);

        expect(comp.participationMode()).toBe('practice');
    });

    it('points the URL at a started practice participation, so the mode survives the editor redirect', () => {
        // Setting the mode alone is not enough. The split panel redirects to the code editor as soon as a participation
        // is available, and that crosses a route-config boundary, so this component is destroyed and re-created. The new
        // instance derives the mode from the URL - and if the redirect went out while the practice start was still in
        // flight, the URL names the graded participation and the practice selection is gone. Writing the URL here is
        // what makes the selection outlive the redirect.
        comp.courseId = 1;
        comp.exercise = { ...exercise, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true, studentParticipations: [] } as unknown as Exercise;
        comp.studentParticipations = [];
        const router = TestBed.inject(Router) as unknown as MockRouter;
        const navigateSpy = vi.spyOn(router, 'navigate');

        comp.onNewParticipation({ id: 680, testRun: true } as StudentParticipation);

        expect(comp.participationMode()).toBe('practice');
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', exercise.id, 'code-editor', 680], expect.objectContaining({ replaceUrl: true }));
    });

    it('does not add a history entry when correcting the URL to the practice participation', () => {
        // replaceUrl, because the address being corrected is one the student never chose: a back navigation must not
        // return them to the graded editor they were never shown.
        comp.courseId = 1;
        comp.exercise = { ...exercise, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true, studentParticipations: [] } as unknown as Exercise;
        comp.studentParticipations = [];
        const router = TestBed.inject(Router) as unknown as MockRouter;
        const navigateSpy = vi.spyOn(router, 'navigate');

        comp.onNewParticipation({ id: 680, testRun: true } as StudentParticipation);

        expect(navigateSpy.mock.calls[0][1]).toMatchObject({ replaceUrl: true });
    });

    it('leaves the URL alone when it already names the started practice participation', () => {
        // The split panel may have routed there already; a second navigation to the same place is churn the router has
        // to resolve and would re-trigger the effects that depend on the child route.
        comp.courseId = 1;
        comp.exercise = { ...exercise, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true, studentParticipations: [] } as unknown as Exercise;
        comp.studentParticipations = [];
        const router = TestBed.inject(Router) as unknown as MockRouter;
        router.setUrl('/courses/1/exercises/programming-exercises/42/code-editor/680');
        const navigateSpy = vi.spyOn(router, 'navigate');

        comp.onNewParticipation({ id: 680, testRun: true } as StudentParticipation);

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should merge websocket submission deltas instead of replacing the attempt history (subscribeForNewResults)', () => {
        // Covers the second call site of mergeSubmissions: the participation-change websocket delivers a participation
        // that may only carry the changed/added submission. Prior attempts must be preserved.
        comp.exercise = { ...exercise } as Exercise;
        const existingParticipation = { id: 555, exercise: comp.exercise, submissions: [{ id: 1 } as Submission, { id: 2 } as Submission] } as StudentParticipation;
        comp.studentParticipations = [existingParticipation];

        comp.subscribeForNewResults();

        const changedParticipation = { id: 555, exercise: { id: comp.exercise!.id }, submissions: [{ id: 3 } as Submission] } as StudentParticipation;
        participationWebsocketBehaviorSubject.next(changedParticipation);

        const merged = comp.studentParticipations.find((p) => p.id === 555);
        expect(merged?.submissions?.map((s) => s.id)).toEqual([1, 2, 3]);
    });

    it('should keep submission history while applying a live transition to finished', () => {
        comp.exercise = { ...exercise } as Exercise;
        const existingParticipation = {
            id: 555,
            exercise: comp.exercise,
            initializationState: InitializationState.INITIALIZED,
            submissions: [{ id: 1 } as Submission, { id: 2 } as Submission],
        } as StudentParticipation;
        comp.studentParticipations = [existingParticipation];

        comp.subscribeForNewResults();

        const changedParticipation = {
            id: 555,
            exercise: { id: comp.exercise!.id },
            initializationState: InitializationState.FINISHED,
            submissions: [{ id: 3 } as Submission],
        } as StudentParticipation;
        participationWebsocketBehaviorSubject.next(changedParticipation);

        const merged = comp.studentParticipations.find((p) => p.id === 555);
        expect(merged?.initializationState).toBe(InitializationState.FINISHED);
        expect(merged?.submissions?.map((s) => s.id)).toEqual([1, 2, 3]);
    });
});
