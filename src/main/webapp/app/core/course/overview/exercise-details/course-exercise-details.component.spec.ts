import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TeamService } from 'app/exercise/team/team.service';
import { CourseExerciseDetailsComponent } from 'app/core/course/overview/exercise-details/course-exercise-details.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/core/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { ResultHistoryComponent } from 'app/exercise/result-history/result-history.component';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { LtiInitializerComponent } from 'app/core/course/overview/exercise-details/lti-initializer/lti-initializer.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { LockRepositoryPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { ProblemStatementComponent } from 'app/core/course/overview/exercise-details/problem-statement/problem-statement.component';
import { ExerciseInfoComponent } from 'app/exercise/exercise-info/exercise-info.component';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { Component, input } from '@angular/core';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { ScienceService } from 'app/shared/science/science.service';

// Simple mock to avoid ng-mocks issues with signal-based viewChild
@Component({
    selector: 'jhi-exercise-chatbot-button',
    template: '',
    standalone: true,
})
class MockIrisExerciseChatbotButtonComponent {
    readonly mode = input<ChatServiceMode>();
}
import { mockCourseSettings } from 'test/helpers/mocks/iris/mock-settings';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { ElementRef, signal } from '@angular/core';
import { ResetRepoButtonComponent } from 'app/core/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('CourseExerciseDetailsComponent', () => {
    setupTestBed({ zoneless: true });

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
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ResultHistoryComponent),
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
                { provide: MetisConversationService, useClass: MockMetisConversationService },
            ],
        }).overrideComponent(CourseExerciseDetailsComponent, {
            remove: { imports: [IrisExerciseChatbotButtonComponent] },
            add: { imports: [MockIrisExerciseChatbotButtonComponent] },
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
        expect(comp.hasMoreResults).toBe(false);
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

        const changedParticipation = cloneDeep(studentParticipation);
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
        expect(comp.exercise!.studentParticipations![0].submissions![0].results![0]).toStrictEqual(changedResult);
        expect(comp.plagiarismCaseInfo).toEqual(plagiarismCaseInfo);
        expect(comp.hasMoreResults).toBe(false);
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

        expect(comp.exampleSolutionInfo).toBeUndefined();
        const newExercise = { ...textExercise };
        comp.showIfExampleSolutionPresent(newExercise);
        expect(comp.exampleSolutionInfo).toBe(exampleSolutionInfo);
        expect(exerciseServiceSpy).toHaveBeenCalledOnce();
        expect(exerciseServiceSpy).toHaveBeenCalledWith(newExercise, artemisMarkdown);
    });

    it('should collapse example solution for tutors', () => {
        expect(comp.exampleSolutionCollapsed).toBeUndefined();
        comp.showIfExampleSolutionPresent({ ...textExercise, isAtLeastTutor: true });
        expect(comp.exampleSolutionCollapsed).toBe(true);

        comp.showIfExampleSolutionPresent({ ...textExercise, isAtLeastTutor: false });
        expect(comp.exampleSolutionCollapsed).toBe(false);
    });

    it('should collapse/expand example solution when clicked', () => {
        expect(comp.exampleSolutionCollapsed).toBeUndefined();
        comp.changeExampleSolution();
        expect(comp.exampleSolutionCollapsed).toBe(true);

        comp.changeExampleSolution();
        expect(comp.exampleSolutionCollapsed).toBe(false);
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

    it('should handle new programming exercise', () => {
        const courseId = programmingExercise.course!.id!;

        comp.courseId = courseId;

        comp.handleNewExercise({ exercise: programmingExercise });
        expect(comp.baseResource).toBe(`/course-management/${courseId}/${programmingExercise.type}-exercises/${programmingExercise.id}/`);
        expect(comp.allowComplaintsForAutomaticAssessments).toBe(true);
        expect(comp.submissionPolicy).toEqual(submissionPolicy);
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
            expect(comp.irisEnabled).toBe(true);
            expect(comp.irisChatEnabled).toBe(true);
        } else {
            // Should not have called getCourseSettings if 'iris' is not active
            expect(getCourseSettingsSpy).not.toHaveBeenCalled();
            expect(comp.irisEnabled).toBe(false);
            expect(comp.irisChatEnabled).toBe(false);
        }
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
});
