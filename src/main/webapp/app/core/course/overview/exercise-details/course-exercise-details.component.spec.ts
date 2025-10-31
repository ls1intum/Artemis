import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
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
import { ScienceService } from 'app/shared/science/science.service';
import { mockCourseSettings } from 'test/helpers/mocks/iris/mock-settings';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { ElementRef, signal } from '@angular/core';
import { ResetRepoButtonComponent } from 'app/core/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('CourseExerciseDetailsComponent', () => {
    let comp: CourseExerciseDetailsComponent;
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;
    let exerciseService: ExerciseService;
    let teamService: TeamService;
    let participationService: ParticipationService;
    let participationWebsocketService: ParticipationWebsocketService;
    let complaintService: ComplaintService;
    let getExerciseDetailsMock: jest.SpyInstance;
    let mergeStudentParticipationMock: jest.SpyInstance;
    let subscribeForParticipationChangesMock: jest.SpyInstance;
    let participationWebsocketBehaviorSubject: BehaviorSubject<Participation | undefined>;
    let scienceService: ScienceService;
    let logEventStub: jest.SpyInstance;

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(DiscussionSectionComponent), FaIconComponent],
            declarations: [
                CourseExerciseDetailsComponent,
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
                MockComponent(ExerciseDetailsStudentActionsComponent),
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                comp = fixture.componentInstance;

                comp.studentParticipations = [];

                // mock exerciseService
                exerciseService = TestBed.inject(ExerciseService);
                getExerciseDetailsMock = jest.spyOn(exerciseService, 'getExerciseDetails');
                getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: exercise } }));

                // mock teamService, needed for team assignment
                teamService = TestBed.inject(TeamService);
                const teamAssignmentPayload = {
                    exerciseId: 2,
                    teamId: 2,
                    studentParticipations: [],
                } as TeamAssignmentPayload;
                jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockReturnValue(Promise.resolve(of(teamAssignmentPayload)));

                // mock participationService, needed for team assignment
                participationWebsocketBehaviorSubject = new BehaviorSubject<Participation | undefined>(undefined);
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                subscribeForParticipationChangesMock = jest.spyOn(participationWebsocketService, 'subscribeForParticipationChanges');
                subscribeForParticipationChangesMock.mockReturnValue(participationWebsocketBehaviorSubject);

                complaintService = TestBed.inject(ComplaintService);

                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');

                participationService = TestBed.inject(ParticipationService);
                mergeStudentParticipationMock = jest.spyOn(participationService, 'mergeStudentParticipations');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        tick(500);
        expect(comp.exerciseId).toBe(42);
        expect(comp.courseId).toBe(1);
        expect(comp.exercise).toStrictEqual(exercise);
        expect(comp.hasMoreResults).toBeFalse();
        comp.ngOnDestroy();
    }));

    it('should have student participations', fakeAsync(() => {
        const studentParticipation = new StudentParticipation();
        studentParticipation.student = new User(99);
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
        jest.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([studentParticipation]);
        jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({} as EntityResponseType));

        // mock participationService, needed for team assignment
        mergeStudentParticipationMock.mockReturnValue([studentParticipation]);
        const changedParticipation = cloneDeep(studentParticipation);
        const changedResult = { ...result, id: 2 };

        changedParticipation.submissions![0].results = [changedResult];
        subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(changedParticipation));

        fixture.detectChanges();
        tick(500);

        // override mock to return exercise with participation
        getExerciseDetailsMock.mockReturnValue(exerciseDetailResponse);
        mergeStudentParticipationMock.mockReturnValue([changedParticipation]);
        comp.loadExercise();
        fixture.detectChanges();
        expect(comp.courseId).toBe(1);
        expect(comp.studentParticipations?.[0].exercise?.id).toBe(exercise.id);
        expect(comp.exercise!.id).toBe(exercise.id);
        expect(comp.exercise!.studentParticipations![0].submissions![0].results![0]).toStrictEqual(changedResult);
        expect(comp.plagiarismCaseInfo).toEqual(plagiarismCaseInfo);
        expect(comp.hasMoreResults).toBeFalse();
        expect(comp.exerciseRatedBadge(result)).toBe('bg-info');
    }));

    it('should not be a quiz exercise', () => {
        comp.exercise = { ...exercise };
        expect(comp.quizExerciseStatus).toBeUndefined();
    });

    it('should configure example solution for exercise', () => {
        const exampleSolutionInfo = {} as ExampleSolutionInfo;
        const exerciseServiceSpy = jest.spyOn(ExerciseService, 'extractExampleSolutionInfo').mockReturnValue(exampleSolutionInfo);

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
        expect(comp.exampleSolutionCollapsed).toBeTrue();

        comp.showIfExampleSolutionPresent({ ...textExercise, isAtLeastTutor: false });
        expect(comp.exampleSolutionCollapsed).toBeFalse();
    });

    it('should collapse/expand example solution when clicked', () => {
        expect(comp.exampleSolutionCollapsed).toBeUndefined();
        comp.changeExampleSolution();
        expect(comp.exampleSolutionCollapsed).toBeTrue();

        comp.changeExampleSolution();
        expect(comp.exampleSolutionCollapsed).toBeFalse();
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
        expect(comp.allowComplaintsForAutomaticAssessments).toBeTrue();
        expect(comp.submissionPolicy).toEqual(submissionPolicy);
    });

    it('should handle error when getting latest rated result', fakeAsync(() => {
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        const error = { message: 'Error msg' };
        const complaintServiceSpy = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(throwError(() => error));

        const submissionId = 55;
        comp.gradedStudentParticipation = { submissions: [{ id: submissionId }] };
        comp.sortedHistoryResults = [{ id: 2 }];
        comp.exercise = { ...exercise };

        comp.loadComplaintAndLatestRatedResult();
        tick();

        expect(complaintServiceSpy).toHaveBeenCalledOnce();
        expect(complaintServiceSpy).toHaveBeenCalledWith(submissionId);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith(error.message);
    }));

    it('should handle participation update', fakeAsync(() => {
        const submissionId = 55;
        const submission = { id: submissionId };
        const participation = { submissions: [submission] };
        comp.gradedStudentParticipation = participation;
        comp.sortedHistoryResults = [{ id: 2 }];
        comp.exercise = { ...programmingExercise };

        comp.courseId = programmingExercise.course!.id!;

        comp.handleNewExercise({ exercise: programmingExercise });
        tick();

        const newParticipation = { ...participation, submissions: [submission, { id: submissionId + 1 }] };

        mergeStudentParticipationMock.mockReturnValue([newParticipation]);

        participationWebsocketBehaviorSubject.next({ ...newParticipation, exercise: programmingExercise });
    }));

    it.each<[string[]]>([[[]], [[PROFILE_IRIS]]])(
        'should load iris settings only if profile iris is active',
        fakeAsync((activeProfiles: string[]) => {
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
            jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles } as any as ProfileInfo);
            jest.spyOn(profileService, 'isProfileActive').mockReturnValue(activeProfiles.includes(PROFILE_IRIS));

            const irisSettingsService = TestBed.inject(IrisSettingsService);
            const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettings').mockReturnValue(of(fakeSettings));

            // Act
            comp.ngOnInit();
            tick();

            if (activeProfiles.includes(PROFILE_IRIS)) {
                // Should have called getCourseSettings if 'iris' is active
                expect(getCourseSettingsSpy).toHaveBeenCalledWith(1);
                expect(comp.irisEnabled).toBeTrue();
                expect(comp.irisChatEnabled).toBeTrue();
            } else {
                // Should not have called getCourseSettings if 'iris' is not active
                expect(getCourseSettingsSpy).not.toHaveBeenCalled();
                expect(comp.irisEnabled).toBeFalse();
                expect(comp.irisChatEnabled).toBeFalse();
            }
        }),
    );

    it('should log event on init', () => {
        fixture.detectChanges();
        expect(logEventStub).toHaveBeenCalledExactlyOnceWith(ScienceEventType.EXERCISE__OPEN, exercise.id);
    });

    it('should not show discussion section when communication is disabled', fakeAsync(() => {
        const newExercise = {
            ...exercise,
            course: { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED },
        };
        getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: newExercise } }));

        fixture.detectChanges();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeFalsy();
    }));

    it('should show discussion section when communication is enabled', fakeAsync(() => {
        fixture.detectChanges();
        tick(500);

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeTruthy();
    }));
});
