import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockParticipationWebsocketService } from '../../../helpers/mocks/service/mock-participation-websocket.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { LtiInitializerComponent } from 'app/overview/exercise-details/lti-initializer.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MockCourseManagementService } from '../../../helpers/mocks/service/mock-course-management.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { LockRepositoryPolicy } from 'app/entities/submission-policy.model';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismCaseInfo } from 'app/exercises/shared/plagiarism/types/PlagiarismCaseInfo';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseHintButtonOverlayComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-button-overlay.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-example-solution-repo-download.component';
import { ResetRepoButtonComponent } from 'app/shared/components/reset-repo-button/reset-repo-button.component';
import { ProblemStatementComponent } from 'app/overview/exercise-details/problem-statement/problem-statement.component';
import { ExerciseInfoComponent } from 'app/exercises/shared/exercise-info/exercise-info.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { PROFILE_IRIS } from 'app/app.constants';

describe('CourseExerciseDetailsComponent', () => {
    let comp: CourseExerciseDetailsComponent;
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;
    let profileService: ProfileService;
    let exerciseService: ExerciseService;
    let teamService: TeamService;
    let participationService: ParticipationService;
    let participationWebsocketService: ParticipationWebsocketService;
    let complaintService: ComplaintService;
    let plagiarismCaseService: PlagiarismCasesService;

    let getProfileInfoMock: jest.SpyInstance;
    let getExerciseDetailsMock: jest.SpyInstance;
    let mergeStudentParticipationMock: jest.SpyInstance;
    let subscribeForParticipationChangesMock: jest.SpyInstance;
    let plagiarismCaseServiceMock: jest.SpyInstance;
    let scienceService: ScienceService;
    let logEventStub: jest.SpyInstance;

    const exercise = { id: 42, type: ExerciseType.TEXT, studentParticipations: [], course: {} } as unknown as Exercise;

    const textExercise = {
        id: 24,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        exampleSolution: 'Example<br>Solution',
    } as unknown as TextExercise;

    const plagiarismCaseInfo = { id: 20, verdict: PlagiarismVerdict.WARNING };

    const parentParams = { courseId: 1 };
    const parentRoute = { parent: { parent: { params: of(parentParams) } } } as any as ActivatedRoute;
    const route = { params: of({ exerciseId: exercise.id }), parent: parentRoute, queryParams: of({ welcome: '' }) } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
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
                MockComponent(ExerciseHintButtonOverlayComponent),
                MockComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent),
                MockComponent(ProblemStatementComponent),
                MockComponent(ResetRepoButtonComponent),
                MockComponent(RatingComponent),
                MockRouterLinkDirective,
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(FaIconComponent),
                MockDirective(ExtensionPointDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(LtiInitializerComponent),
                MockComponent(ModelingEditorComponent),
                MockComponent(ExerciseInfoComponent),
            ],
            providers: [
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
                MockProvider(GuidedTourService),
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

                // mock profileService
                profileService = fixture.debugElement.injector.get(ProfileService);
                getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                const profileInfo = { inProduction: false } as ProfileInfo;
                const profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoMock.mockReturnValue(profileInfoSubject);

                // mock exerciseService
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                getExerciseDetailsMock = jest.spyOn(exerciseService, 'getExerciseDetails');
                getExerciseDetailsMock.mockReturnValue(of({ body: exercise }));

                // mock teamService, needed for team assignment
                teamService = fixture.debugElement.injector.get(TeamService);
                const teamAssignmentPayload = { exerciseId: 2, teamId: 2, studentParticipations: [] } as TeamAssignmentPayload;
                jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockReturnValue(Promise.resolve(of(teamAssignmentPayload)));

                // mock participationService, needed for team assignment
                participationWebsocketService = fixture.debugElement.injector.get(ParticipationWebsocketService);
                subscribeForParticipationChangesMock = jest.spyOn(participationWebsocketService, 'subscribeForParticipationChanges');
                subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(undefined));

                complaintService = fixture.debugElement.injector.get(ComplaintService);

                // mock plagiarismCaseService used when loading exercises
                plagiarismCaseService = fixture.debugElement.injector.get(PlagiarismCasesService);
                plagiarismCaseServiceMock = jest
                    .spyOn(plagiarismCaseService, 'getPlagiarismCaseInfoForStudent')
                    .mockReturnValue(of({ body: plagiarismCaseInfo } as HttpResponse<PlagiarismCaseInfo>));

                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        tick(500);
        expect(comp.isProduction).toBeFalse();
        expect(comp.exerciseId).toBe(42);
        expect(comp.courseId).toBe(1);
        expect(comp.exercise).toStrictEqual(exercise);
        expect(comp.hasMoreResults).toBeFalse();
        comp.ngOnDestroy();
    }));

    it('should have student participations', fakeAsync(() => {
        const studentParticipation = new StudentParticipation();
        studentParticipation.student = new User(99);
        studentParticipation.submissions = [new TextSubmission()];
        studentParticipation.type = ParticipationType.STUDENT;
        studentParticipation.id = 42;
        const result = new Result();
        result.id = 1;
        result.completionDate = dayjs();
        studentParticipation.results = [result];
        studentParticipation.exercise = exercise;

        const exerciseDetail = { ...exercise, studentParticipations: [studentParticipation] };
        const exerciseDetailResponse = of({ body: exerciseDetail });

        // return initial participation for websocketService
        jest.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([studentParticipation]);
        jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({} as EntityResponseType));

        // mock participationService, needed for team assignment
        participationService = TestBed.inject(ParticipationService);
        mergeStudentParticipationMock = jest.spyOn(participationService, 'mergeStudentParticipations');
        mergeStudentParticipationMock.mockReturnValue([studentParticipation]);
        const changedParticipation = cloneDeep(studentParticipation);
        const changedResult = { ...result, id: 2 };
        changedParticipation.results = [changedResult];
        subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(changedParticipation));

        fixture.detectChanges();
        tick(500);

        // override mock to return exercise with participation
        getExerciseDetailsMock.mockReturnValue(exerciseDetailResponse);
        mergeStudentParticipationMock.mockReturnValue([changedParticipation]);
        comp.loadExercise();
        fixture.detectChanges();
        expect(comp.courseId).toBe(1);
        expect(comp.studentParticipations?.[0].exercise?.id).toBe(exerciseDetail.id);
        expect(comp.exercise!.id).toBe(exercise.id);
        expect(comp.exercise!.studentParticipations![0].results![0]).toStrictEqual(changedResult);
        expect(comp.plagiarismCaseInfo).toEqual(plagiarismCaseInfo);
        expect(comp.hasMoreResults).toBeFalse();
        expect(comp.exerciseRatedBadge(result)).toBe('bg-info');
        expect(plagiarismCaseServiceMock).toHaveBeenCalledTimes(2);
        expect(plagiarismCaseServiceMock).toHaveBeenCalledWith(1, exercise.id);
    }));

    it('should not be a quiz exercise', () => {
        comp.exercise = { ...exercise };
        expect(comp.quizExerciseStatus).toBeUndefined();
    });

    it('should configure example solution for exercise', () => {
        const exampleSolutionInfo = {} as ExampleSolutionInfo;
        const exerciseServiceSpy = jest.spyOn(ExerciseService, 'extractExampleSolutionInfo').mockReturnValue(exampleSolutionInfo);

        const artemisMarkdown = fixture.debugElement.injector.get(ArtemisMarkdownService);

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

    it('should store a reference to child component', () => {
        comp.exercise = exercise;

        const childComponent = {} as DiscussionSectionComponent;
        comp.onChildActivate(childComponent);
        expect(childComponent.exercise).toEqual(exercise);
    });

    it('should activate hint', () => {
        comp.availableExerciseHints = [{ id: 1 }, { id: 2 }];
        comp.activatedExerciseHints = [];

        const activatedHint = comp.availableExerciseHints[0];
        comp.onHintActivated(activatedHint);
        expect(comp.availableExerciseHints).not.toContain(activatedHint);
        expect(comp.activatedExerciseHints).toContain(activatedHint);
    });

    it('should handle new programming exercise', () => {
        const submissionPolicyService = fixture.debugElement.injector.get(SubmissionPolicyService);
        const submissionPolicy = new LockRepositoryPolicy();
        const submissionPolicyServiceSpy = jest.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(submissionPolicy));

        const programmingExercise = {
            id: exercise.id,
            type: ExerciseType.PROGRAMMING,
            studentParticipations: [],
            course: { id: 2 },
            allowComplaintsForAutomaticAssessments: true,
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: true,
            numberOfAssessmentsOfCorrectionRounds: [],
        } as ProgrammingExercise;

        const childComponent = {} as DiscussionSectionComponent;
        comp.onChildActivate(childComponent);

        const courseId = programmingExercise.course!.id!;

        comp.courseId = courseId;

        comp.handleNewExercise(programmingExercise);
        expect(comp.baseResource).toBe(`/course-management/${courseId}/${programmingExercise.type}-exercises/${programmingExercise.id}/`);
        expect(comp.allowComplaintsForAutomaticAssessments).toBeTrue();
        expect(submissionPolicyServiceSpy).toHaveBeenCalledOnce();
        expect(comp.submissionPolicy).toEqual(submissionPolicy);
        expect(childComponent.exercise).toEqual(programmingExercise);
    });

    it('should handle error when getting latest rated result', fakeAsync(() => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        const error = { message: 'Error msg' };
        const complaintServiceSpy = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(throwError(error));

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

    it.each<[string[]]>([[[]], [[PROFILE_IRIS]]])(
        'should load iris settings only if profile iris is active',
        fakeAsync((activeProfiles: string[]) => {
            // Setup
            const programmingExercise = {
                id: 42,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [],
                course: {},
            } as unknown as ProgrammingExercise;

            const fakeSettings = {} as any as IrisSettings;

            const irisSettingsService = TestBed.inject(IrisSettingsService);
            const getCombinedProgrammingExerciseSettingsMock = jest.spyOn(irisSettingsService, 'getCombinedProgrammingExerciseSettings');
            getCombinedProgrammingExerciseSettingsMock.mockReturnValue(of(fakeSettings));

            const submissionPolicyService = TestBed.inject(SubmissionPolicyService);
            const submissionPolicy = new LockRepositoryPolicy();
            jest.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(submissionPolicy));

            getExerciseDetailsMock.mockReturnValue(of({ body: programmingExercise }));

            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of({ activeProfiles } as any as ProfileInfo));

            // Act
            comp.ngOnInit();
            tick();

            if (activeProfiles.includes(PROFILE_IRIS)) {
                // Should have called getCombinedProgrammingExerciseSettings if 'iris' is active
                expect(getCombinedProgrammingExerciseSettingsMock).toHaveBeenCalled();
                expect(comp.irisSettings).toBe(fakeSettings);
            } else {
                // Should not have called getCombinedProgrammingExerciseSettings if 'iris' is not active
                expect(getCombinedProgrammingExerciseSettingsMock).not.toHaveBeenCalled();
                expect(comp.irisSettings).toBeUndefined();
            }
        }),
    );

    it('should log event on init', () => {
        fixture.detectChanges();
        expect(logEventStub).toHaveBeenCalledExactlyOnceWith(ScienceEventType.EXERCISE__OPEN, exercise.id);
    });
});
