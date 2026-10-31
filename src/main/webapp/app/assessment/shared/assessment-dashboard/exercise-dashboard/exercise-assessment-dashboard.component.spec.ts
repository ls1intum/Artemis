import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { SidePanelComponent } from 'app/shared-ui/side-panel/side-panel.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { TutorParticipationGraphComponent } from 'app/exercise/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/exercise/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { TutorParticipationDTO, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Language } from 'app/course/shared/entities/course.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercise/submission/submission.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ExtensionPointDirective } from 'app/foundation/extension-point/extension-point.directive';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ArtemisNavigationUtilService, getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { SortService } from 'app/foundation/service/sort.service';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { TutorLeaderboardElement } from 'app/exercise/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AssessmentQueueComponent } from './assessment-queue/assessment-queue.component';
import { ExerciseAssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { InfoPanelComponent } from 'app/assessment/shared/info-panel/info-panel.component';
import { SecondCorrectionEnableButtonComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/second-correction-button/second-correction-enable-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import dayjs from 'dayjs/esm';
import { ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING } from 'app/assessment/shared/util/assessment-availability.util';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('ExerciseAssessmentDashboardComponent', () => {
    let comp: ExerciseAssessmentDashboardComponent;
    let fixture: ComponentFixture<ExerciseAssessmentDashboardComponent>;

    let exerciseService: ExerciseService;
    let exerciseServiceGetForTutorsStub: MockInstance;
    let exerciseServiceGetStatsForTutorsStub: MockInstance;

    let tutorParticipationService: TutorParticipationService;

    let accountService: AccountService;

    let translateService: TranslateService;

    let submissionService: SubmissionService;

    // The correction round is stated explicitly rather than implied by the position in the results array.
    const result1 = { id: 11, correctionRound: 0 } as Result;
    const exam = { id: 13, numberOfCorrectionRoundsInExam: 2 } as Exam;
    const exerciseGroup = { id: 14, exam } as ExerciseGroup;

    const exercise = {
        id: 15,
        exerciseGroup,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const modelingExercise = {
        id: 17,
        exerciseGroup,
        type: ExerciseType.MODELING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        exampleSolutionModel: '{"version": "3.0.0", "elements": {}, "relationships": {}}',
        exampleSolutionExplanation: 'explanation',
    } as ModelingExercise;
    const textSubmission = { id: 23, submissionExerciseType: SubmissionExerciseType.TEXT, language: Language.ENGLISH } as TextSubmission;
    const programmingSubmission = { id: 24 } as ProgrammingSubmission;

    const numberOfAssessmentsOfCorrectionRounds = [
        { inTime: 1, late: 1 },
        { inTime: 8, late: 0 },
    ];
    const numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = [
        { inTime: 2, late: 0 },
        { inTime: 7, late: 0 },
    ];
    const stats = {
        numberOfSubmissions: { inTime: 12, late: 5 },
        totalNumberOfAssessments: 9,
        numberOfAssessmentsOfCorrectionRounds,
        numberOfLockedAssessmentByOtherTutorsOfCorrectionRound,
    } as StatsForDashboard;

    const submissionWithComplaintDTO = {
        submission: {
            id: 23,
            results: [result1],
        },
        complaint: {
            result: result1,
        },
    } as SubmissionWithComplaintDTO;

    let navigateSpy: MockInstance;
    const route = {
        snapshot: {
            paramMap: convertToParamMap({
                courseId: 1,
                examId: 2,
                exerciseId: modelingExercise.id!,
            }),
        },
    } as any as ActivatedRoute;
    const imports = [
        RouterModule.forRoot([]),
        ExerciseAssessmentDashboardComponent,
        FaIconComponent,
        MockComponent(TutorLeaderboardComponent),
        MockComponent(TutorParticipationGraphComponent),
        MockComponent(HeaderExercisePageWithDetailsComponent),
        MockComponent(SidePanelComponent),
        MockComponent(InfoPanelComponent),
        MockComponent(ModelingEditorComponent),
        MockComponent(SecondCorrectionEnableButtonComponent),
        MockComponent(CollapsableAssessmentInstructionsComponent),
        MockComponent(StructuredGradingInstructionsAssessmentLayoutComponent),
        MockComponent(ProgrammingExerciseInstructionComponent),
        MockComponent(ButtonComponent),
        MockComponent(ResultComponent),
        MockDirective(ExtensionPointDirective),
        MockHasAnyAuthorityDirective,
        MockTranslateValuesDirective,
        MockPipe(ArtemisDatePipe),
        MockPipe(ArtemisTranslatePipe),
    ];

    const providers = [
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: AccountService, useClass: MockAccountService },
        { provide: ActivatedRoute, useValue: route },
        LocalStorageService,
        SessionStorageService,
        { provide: ProfileService, useClass: MockProfileService },
        MockProvider(ExerciseService),
        MockProvider(AlertService),
        MockProvider(TutorParticipationService),
        MockProvider(ArtemisMarkdownService),
        MockProvider(ComplaintService),
        MockProvider(ArtemisDatePipe),
        MockProvider(SortService),
        MockProvider(ArtemisNavigationUtilService),
        { provide: DialogService, useClass: MockDialogService },
        provideHttpClient(),
        provideHttpClientTesting(),
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports,
            providers,
        })
            .overrideComponent(ExerciseAssessmentDashboardComponent, {
                remove: { imports: [TutorLeaderboardComponent, AssessmentQueueComponent] },
                add: { imports: [MockComponent(TutorLeaderboardComponent), MockComponent(AssessmentQueueComponent)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ExerciseAssessmentDashboardComponent);
        comp = fixture.componentInstance;
        exerciseService = TestBed.inject(ExerciseService);
        submissionService = TestBed.inject(SubmissionService);
        vi.spyOn(submissionService, 'getSubmissionsWithComplaintsForTutor').mockReturnValue(of(new HttpResponse({ body: [] })));
        const router = fixture.debugElement.injector.get(Router);
        navigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
        tutorParticipationService = TestBed.inject(TutorParticipationService);
        exerciseServiceGetForTutorsStub = vi.spyOn(exerciseService, 'getForTutors');
        exerciseServiceGetStatsForTutorsStub = vi.spyOn(exerciseService, 'getStatsForTutors');
        exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: modelingExercise, headers: new HttpHeaders() })));
        exerciseServiceGetStatsForTutorsStub.mockReturnValue(of(new HttpResponse({ body: stats, headers: new HttpHeaders() })));
        comp.exerciseId.set(modelingExercise.id!);
        comp.submissionsWithComplaints.set([submissionWithComplaintDTO]);
        accountService = TestBed.inject(AccountService);
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('shows the warning when the course-wide draft limit is reached', () => {
        accountService.userIdentity.set({ id: 10, login: 'tutor1' } as User);
        fixture.detectChanges();
        comp.exercise.set({ ...comp.exercise(), isAtLeastInstructor: true });
        comp.statsForDashboard.set(Object.assign(new StatsForDashboard(), stats, { numberOfAssessmentLocks: 10 }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[jhiTranslate="artemisApp.submission.lockedSubmissionsLimitReached"]')).not.toBeNull();
    });

    it('should initialize', () => {
        const user = { id: 10, login: 'tutor1' } as User;
        accountService.userIdentity.set(user);
        fixture.detectChanges();

        expect(comp.courseId()).toBe(1);
        expect(comp.examId).toBe(2);
        expect(comp.exerciseId()).toBe(modelingExercise.id);

        expect(comp.tutor()).toEqual(user);

        const setupGraphSpy = vi.spyOn(comp, 'setupGraph');

        translateService.use('en'); // Change language.
        expect(setupGraphSpy).toHaveBeenCalledTimes(1);
    });

    it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD])(
        'uses separate redesigned correction queues for %s exam exercises',
        (type) => {
            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: { ...modelingExercise, type } })));
            fixture.detectChanges();
            const queues = fixture.debugElement.queryAll(By.directive(AssessmentQueueComponent));
            expect(queues).toHaveLength(2);
            expect(queues.map((queue) => queue.componentInstance.correctionRound())).toEqual([0, 1]);
            expect(fixture.nativeElement.querySelector('[data-testid="start-new-assessment"]')).toBeNull();
            expect(fixture.nativeElement.querySelector('#continue-assessment')).toBeNull();
        },
    );

    it('keeps one redesigned queue for course exercises', () => {
        fixture.detectChanges();
        comp.isExamMode.set(false);
        comp.exercise.set({ ...comp.exercise(), exerciseGroup: undefined, course: { id: 1 } });
        fixture.detectChanges();
        const queues = fixture.debugElement.queryAll(By.directive(AssessmentQueueComponent));
        expect(queues).toHaveLength(1);
        expect(queues[0].componentInstance.correctionRound()).toBeUndefined();
    });

    it('should hide the complaints shortcut when the course has complaints disabled', () => {
        accountService.userIdentity.set({ id: 10, login: 'tutor1' } as User);
        exerciseServiceGetStatsForTutorsStub.mockReturnValue(
            of(new HttpResponse({ body: { ...stats, complaintsEnabled: false } as StatsForDashboard, headers: new HttpHeaders() })),
        );

        fixture.detectChanges();

        expect(comp.complaintsEnabled()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-complaints"]')).toBeNull();
    });

    it('should show the complaints shortcut when the course has complaints enabled', () => {
        accountService.userIdentity.set({ id: 10, login: 'tutor1' } as User);
        exerciseServiceGetStatsForTutorsStub.mockReturnValue(
            of(new HttpResponse({ body: { ...stats, complaintsEnabled: true } as StatsForDashboard, headers: new HttpHeaders() })),
        );

        fixture.detectChanges();

        expect(comp.complaintsEnabled()).toBe(true);
        const complaintsLink = fixture.nativeElement.querySelector('[data-testid="exercise-complaints"]') as HTMLAnchorElement;
        expect(complaintsLink).not.toBeNull();
        expect(complaintsLink.getAttribute('aria-label')).toBe('artemisApp.exercise.complaints');
    });

    it('should initialize with tutor leaderboard entry', () => {
        const tutor = { id: 10, login: 'tutor1' } as User;
        accountService.userIdentity.set(tutor);
        const tutorLeaderBoardEntry = {
            userId: tutor.id!,
            numberOfAssessments: 3,
            numberOfTutorComplaints: 2,
            numberOfTutorMoreFeedbackRequests: 1,
            numberOfTutorRatings: 4,
        } as TutorLeaderboardElement;

        const statsWithTutor = {
            ...stats,
            tutorLeaderboardEntries: [tutorLeaderBoardEntry],
        } as StatsForDashboard;

        exerciseServiceGetStatsForTutorsStub.mockReturnValue(of(new HttpResponse({ body: statsWithTutor, headers: new HttpHeaders() })));

        fixture.detectChanges();

        expect(comp.numberOfTutorAssessments()).toBe(tutorLeaderBoardEntry.numberOfAssessments);
        expect(comp.complaintsDashboardInfo().tutor).toBe(tutorLeaderBoardEntry.numberOfTutorComplaints);
        expect(comp.moreFeedbackRequestsDashboardInfo().tutor).toBe(tutorLeaderBoardEntry.numberOfTutorMoreFeedbackRequests);
        expect(comp.ratingsDashboardInfo().tutor).toBe(tutorLeaderBoardEntry.numberOfTutorRatings);

        const setupGraphSpy = vi.spyOn(comp, 'setupGraph');

        translateService.use('en'); // Change language.
        expect(setupGraphSpy).toHaveBeenCalledTimes(1);
    });

    it('should set exam and stats properties', () => {
        expect(comp.exam()).toBeUndefined();

        comp.loadAll();
        expect(comp.exercise().id).toBe(modelingExercise.id);
        expect(comp.exam()).toEqual(exam);
        expect(comp.exam()?.numberOfCorrectionRoundsInExam).toBe(numberOfAssessmentsOfCorrectionRounds.length);
        expect(comp.numberOfAssessmentsOfCorrectionRounds()).toEqual(numberOfAssessmentsOfCorrectionRounds);
    });

    it('should call hasBeenCompletedByTutor', () => {
        comp.exampleSubmissionsCompletedByTutor.set([{ id: 1 }, { id: 2 }]);
        expect(comp.hasBeenCompletedByTutor(1)).toBe(true);
    });

    it('should call readInstruction', () => {
        const tutorParticipationServiceCreateStub = vi.spyOn(tutorParticipationService, 'create');
        const dto: TutorParticipationDTO = {
            id: 1,
            exerciseId: comp.exerciseId(),
            tutorId: 2,
            status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS,
        };

        tutorParticipationServiceCreateStub.mockImplementation(() => {
            expect(comp.isLoading()).toBe(true);
            return of(new HttpResponse({ body: dto, headers: new HttpHeaders() }));
        });

        expect(comp.tutorParticipation()).toBeUndefined();
        expect(comp.isLoading()).toBe(false);

        comp.readInstruction();

        expect(tutorParticipationServiceCreateStub).toHaveBeenCalledTimes(1);
        expect(tutorParticipationServiceCreateStub).toHaveBeenCalledWith(comp.exerciseId());

        expect(comp.isLoading()).toBe(false);

        expect(comp.tutorParticipation()).toEqual(dto);
        expect(comp.tutorParticipationStatus()).toEqual(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
    });

    describe('getAssessmentLink', () => {
        const fakeExerciseType = ExerciseType.TEXT;
        const fakeCourseId = 42;
        const fakeExerciseId = 1337;
        const fakeExamId = 69;
        const fakeExerciseGroupId = 27;
        it('Expect new submission to delegate correct link', () => {
            const submission = 'new';
            initComponent();
            const expectedParticipationId = undefined;
            const expectedSubmissionUrlParameter = 'new';
            testLink(expectedParticipationId, expectedSubmissionUrlParameter, submission);
        });

        it('Expect existing submission without participation to delegate correct link', () => {
            const submission = { id: 42 };
            initComponent();
            const expectedParticipationId = undefined;
            const expectedSubmissionUrlParameter = 42;
            testLink(expectedParticipationId, expectedSubmissionUrlParameter, submission);
        });

        it('Expect existing submission with participation to delegate correct link', () => {
            const submission = { id: 42, participation: { id: 1337 } };
            initComponent();
            const expectedParticipationId = 1337;
            const expectedSubmissionUrlParameter = 42;
            testLink(expectedParticipationId, expectedSubmissionUrlParameter, submission);
        });

        function initComponent() {
            comp.exercise.set({
                type: fakeExerciseType,
                numberOfAssessmentsOfCorrectionRounds: [],
                studentAssignedTeamIdComputed: false,
                secondCorrectionEnabled: false,
            });
            comp.courseId.set(fakeCourseId);
            comp.exerciseId.set(fakeExerciseId);
            comp.examId = fakeExamId;
            comp.exerciseGroupId = fakeExerciseGroupId;
        }

        function testLink(expectedParticipationId: number | undefined, expectedSubmissionUrlParameter: number | 'new', submission: Submission | 'new') {
            const expectedLink = getLinkToSubmissionAssessment(
                fakeExerciseType,
                fakeCourseId,
                fakeExerciseId,
                expectedParticipationId,
                expectedSubmissionUrlParameter,
                fakeExamId,
                fakeExerciseGroupId,
            );

            const link = comp.getAssessmentLink(submission);

            expect(link).toEqual(expectedLink);
        }
    });

    describe('getComplaintQueryParams', () => {
        it('Expect more feedback request to delegate the correct query', () => {
            const moreFeedbackComplaint = { complaintType: ComplaintType.MORE_FEEDBACK };
            const arrayLength = 42;
            comp.numberOfAssessmentsOfCorrectionRounds.set(new Array(arrayLength));
            const complaintQuery = comp.getComplaintQueryParams(moreFeedbackComplaint);

            expect(complaintQuery).toEqual(comp.getAssessmentQueryParams(arrayLength - 1));
        });

        it('Expect complaint with not present submission to resolve undefined', () => {
            const submission = {
                id: 8,
            };
            const complaintComplaint = {
                complaintType: ComplaintType.COMPLAINT,
                result: { submission },
            };
            const complaintQuery = comp.getComplaintQueryParams(complaintComplaint);

            expect(complaintQuery).toBeUndefined();
        });

        it('Expect present complaint to delegate the correct query', () => {
            const fakeResults = [
                { assessmentType: AssessmentType.MANUAL },
                { assessmentType: AssessmentType.SEMI_AUTOMATIC },
                { assessmentType: AssessmentType.SEMI_AUTOMATIC },
                { assessmentType: AssessmentType.MANUAL },
            ];
            const submission = {
                id: 8,
                results: fakeResults,
            };
            const complaintComplaint = {
                complaintType: ComplaintType.COMPLAINT,
                result: { submission },
            };
            comp.submissionsWithComplaints.set([{ submission, complaint: complaintComplaint }]);
            const complaintQuery = comp.getComplaintQueryParams(complaintComplaint);

            expect(complaintQuery).toEqual(comp.getAssessmentQueryParams(fakeResults.length - 1));
        });
    });

    describe('getSubmissionToViewFromComplaintSubmission', () => {
        it('Expect not present submission to resolve undefined', () => {
            const fakeDTOList: SubmissionWithComplaintDTO[] = [];
            const inputSubmission = { id: 1 };
            comp.submissionsWithComplaints.set(fakeDTOList);
            const submissionToView = comp.getSubmissionToViewFromComplaintSubmission(inputSubmission);

            expect(submissionToView).toBeUndefined();
        });

        it('Expect submission without results to gain an empty list', () => {
            const fakeDTOList: SubmissionWithComplaintDTO[] = [{ submission: { id: 1 }, complaint: {} }];
            const expectedSubmission = { id: 1, results: [] };
            const inputSubmission = { id: 1 };
            comp.submissionsWithComplaints.set(fakeDTOList);
            const submissionToView = comp.getSubmissionToViewFromComplaintSubmission(inputSubmission);

            expect(submissionToView).toEqual(expectedSubmission);
        });

        it('Expect only non automatic results to be returned', () => {
            const fakeResults = [
                { assessmentType: AssessmentType.MANUAL },
                { assessmentType: AssessmentType.AUTOMATIC },
                { assessmentType: AssessmentType.SEMI_AUTOMATIC },
                { assessmentType: AssessmentType.AUTOMATIC },
            ];
            const fakeDTOList: SubmissionWithComplaintDTO[] = [{ submission: { id: 1, results: fakeResults }, complaint: {} }];
            const expectedSubmissionToView = { id: 1, results: [{ assessmentType: AssessmentType.MANUAL }, { assessmentType: AssessmentType.SEMI_AUTOMATIC }] };
            const inputSubmission = { id: 1 };
            comp.submissionsWithComplaints.set(fakeDTOList);
            const submissionToView = comp.getSubmissionToViewFromComplaintSubmission(inputSubmission);

            expect(submissionToView).toEqual(expectedSubmissionToView);
        });
    });

    describe('openExampleSubmission', () => {
        const courseId = 4;

        it('should not openExampleSubmission', () => {
            const submission = { id: 8 };
            comp.openExampleSubmission(submission!.id);
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should openExampleSubmission', () => {
            comp.exercise.set(exercise);
            comp.exercise().type = ExerciseType.PROGRAMMING;
            comp.courseId.set(4);
            comp.exercise.set(exercise);
            const submission = { id: 8 };
            comp.openExampleSubmission(submission!.id, true, true);
            expect(navigateSpy).toHaveBeenCalledWith([`/course-management/${courseId}/${exercise.type}-exercises/${exercise.id}/example-submissions/${submission.id}`], {
                queryParams: { readOnly: true, toComplete: true },
            });
        });
    });

    it('generate exercise detail link', () => {
        comp.exercise.set(modelingExercise);
        comp.courseId.set(4);
        const exerciseDetailsLink = comp.exerciseDetailsLink();
        expect(exerciseDetailsLink).toEqual(['/course-management', 4, ExerciseType.MODELING + '-exercises', modelingExercise.id]);
    });

    it('should toggle second correction', () => {
        comp.exercise.set(exercise);
        comp.exercise().type = ExerciseType.TEXT;

        const secondCorrectionEnabled = true;
        vi.spyOn(exerciseService, 'toggleSecondCorrection').mockImplementation((exerciseId) => {
            expect(comp.togglingSecondCorrectionButton()).toBe(secondCorrectionEnabled);

            expect(exerciseId).toBe(comp.exerciseId());
            return of(secondCorrectionEnabled);
        });

        comp.toggleSecondCorrection();

        expect(comp.togglingSecondCorrectionButton()).toBe(false);
        expect(comp.secondCorrectionEnabled()).toBe(secondCorrectionEnabled);
        expect(comp.numberOfCorrectionRoundsEnabled()).toBe(2);
    });

    it('should place the second correction toggle in the second correction round', () => {
        const instructorExercise = deepClone(modelingExercise);
        instructorExercise.isAtLeastInstructor = true;
        exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: instructorExercise, headers: new HttpHeaders() })));

        fixture.detectChanges();
        comp.numberOfAssessmentsOfCorrectionRounds.update((correctionRounds) => correctionRounds.slice(0, 1));
        fixture.detectChanges();

        const firstCorrectionRound = fixture.nativeElement.querySelector('[data-testid="correction-round-0"]');
        const secondCorrectionRound = fixture.nativeElement.querySelector('[data-testid="correction-round-1"]');
        const secondCorrectionToggles = fixture.nativeElement.querySelectorAll('[data-testid="toggle-second-correction"]');

        expect(firstCorrectionRound).not.toBeNull();
        expect(firstCorrectionRound.querySelector('[data-testid="toggle-second-correction"]')).toBeNull();
        expect(secondCorrectionToggles).toHaveLength(1);
        expect(secondCorrectionRound.querySelector('[data-testid="toggle-second-correction"]')).toBe(secondCorrectionToggles[0]);
    });

    it('should show the second correction toggle before the instructor participates', () => {
        const instructorExercise = deepClone(modelingExercise);
        instructorExercise.isAtLeastInstructor = true;
        instructorExercise.tutorParticipations = [{ status: TutorParticipationStatus.NOT_PARTICIPATED }];
        exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: instructorExercise, headers: new HttpHeaders() })));

        fixture.detectChanges();

        const secondCorrectionRound = fixture.nativeElement.querySelector('[data-testid="correction-round-1"]');

        expect(secondCorrectionRound.querySelector('[data-testid="toggle-second-correction"]')).not.toBeNull();
    });

    it('restricts the test-run queue to one correction round', () => {
        fixture.detectChanges();
        comp.secondCorrectionEnabled.set(true);
        fixture.detectChanges();
        let queue = fixture.debugElement.query(By.directive(AssessmentQueueComponent)).componentInstance as AssessmentQueueComponent;
        expect(queue.correctionRoundsEnabled()).toBe(2);

        comp.isTestRun.set(true);
        fixture.detectChanges();
        queue = fixture.debugElement.query(By.directive(AssessmentQueueComponent)).componentInstance as AssessmentQueueComponent;
        expect(queue.correctionRoundsEnabled()).toBe(1);
    });

    it('should check if complaint locked', () => {
        comp.exercise.set(exercise);
        const complaintService = TestBed.inject(ComplaintService);
        const complaintServiceSpy = vi.spyOn(complaintService, 'isComplaintLockedForLoggedInUser');

        const complaint: Complaint = { id: 20 };
        comp.isComplaintLocked(complaint);

        expect(complaintServiceSpy).toHaveBeenCalledWith(complaint, exercise);
    });

    it('should get submissions with more feedback requests for tutor', () => {
        const submissionServiceSpy = vi.spyOn(submissionService, 'getSubmissionsWithMoreFeedbackRequestsForTutor');
        submissionServiceSpy.mockReturnValue(of(new HttpResponse({ body: [] })));

        const sortMoreFeedbackRowsSpy = vi.spyOn(comp, 'sortMoreFeedbackRows');

        fixture.detectChanges();

        expect(sortMoreFeedbackRowsSpy).toHaveBeenCalledTimes(1);

        submissionServiceSpy.mockReturnValue(throwError(() => errorResponse));

        const errorStatus = 400;
        const errorResponse = new HttpErrorResponse({ status: errorStatus });

        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = vi.spyOn(alertService, 'error');

        comp.loadAll();

        expect(alertServiceSpy).toHaveBeenCalledTimes(1);
        expect(alertServiceSpy).toHaveBeenCalledWith('error.http.400');
    });

    it('should sort more feedback rows', () => {
        const sortService = TestBed.inject(SortService);
        const sortServicePropertySpy = vi.spyOn(sortService, 'sortByProperty');

        comp.sortMoreFeedbackRows();

        expect(sortServicePropertySpy).toHaveBeenCalledTimes(2);
        expect(sortServicePropertySpy).toHaveBeenNthCalledWith(1, comp.submissionsWithMoreFeedbackRequests(), 'complaint.submittedTime', true);
        expect(sortServicePropertySpy).toHaveBeenNthCalledWith(2, comp.submissionsWithMoreFeedbackRequests(), 'complaint.accepted', false);

        comp.sortPredicates[2] = 'responseTime';
        const sortServiceFunctionSpy = vi.spyOn(sortService, 'sortByFunction');

        comp.sortMoreFeedbackRows();

        expect(sortServiceFunctionSpy).toHaveBeenCalledWith(comp.submissionsWithMoreFeedbackRequests(), expect.any(Function), false);
    });

    it('should return submission language', () => {
        expect(comp.language(textSubmission)).toBe(textSubmission.language);
        const unkownLanguage = 'UNKNOWN';
        const textSubmissionWithoutLanguage = {
            id: 23,
            submissionExerciseType: SubmissionExerciseType.TEXT,
        };
        expect(comp.language(textSubmissionWithoutLanguage)).toBe(unkownLanguage);
        expect(comp.language(programmingSubmission)).toBe(unkownLanguage);
    });

    describe('assessment not possible yet', () => {
        const exerciseWithRunningExam = {
            ...modelingExercise,
            latestExamEndDate: dayjs().add(1, 'hour'),
            assessmentPossibleFrom: dayjs().add(1, 'hour'),
        } as ModelingExercise;

        it('should explain why assessment is not possible yet while the exam is still running', () => {
            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: exerciseWithRunningExam, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(comp.assessmentNotPossibleYetReason()).toEqual({
                translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`,
                date: exerciseWithRunningExam.latestExamEndDate,
                assessmentPossibleFrom: exerciseWithRunningExam.assessmentPossibleFrom,
            });
        });

        it('should keep test runs assessable, because they happen before the exam starts', () => {
            comp.exercise.set(exerciseWithRunningExam);

            expect(comp.assessmentNotPossibleYetReason()).toBeDefined();

            comp.isTestRun.set(true);

            expect(comp.assessmentNotPossibleYetReason()).toBeUndefined();
            expect(comp.assessmentNotPossibleYetTooltip()).toBe('');
        });

        it('should re-enable assessment once the moment it becomes possible has passed, without a page reload', () => {
            vi.useFakeTimers();
            try {
                const assessmentPossibleFrom = dayjs().add(5, 'minutes');
                exerciseServiceGetForTutorsStub.mockReturnValue(
                    of(new HttpResponse({ body: { ...modelingExercise, latestExamEndDate: assessmentPossibleFrom, assessmentPossibleFrom } as ModelingExercise })),
                );

                comp.loadAll();
                expect(comp.assessmentNotPossibleYetReason()).toBeDefined();

                vi.advanceTimersByTime(5 * 60 * 1000 + 2000);

                expect(comp.assessmentNotPossibleYetReason()).toBeUndefined();
            } finally {
                vi.useRealTimers();
            }
        });

        it('should render the explanation and make the assessment actions unreachable, also via the keyboard', () => {
            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: exerciseWithRunningExam, headers: new HttpHeaders() })));

            fixture.detectChanges();

            const banner = fixture.nativeElement.querySelector('#assessment-not-possible-yet');
            expect(banner).not.toBeNull();

            const queue = fixture.debugElement.query(By.directive(AssessmentQueueComponent)).componentInstance as AssessmentQueueComponent;
            expect(queue.assessmentDisabled()).toBe(true);
            expect(queue.assessmentDisabledTooltip()).not.toBe('');
        });

        it('should not overflow the timer delay for an exam that is still more than 24 days away', () => {
            vi.useFakeTimers();
            const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
            try {
                const assessmentPossibleFrom = dayjs().add(60, 'days');
                exerciseServiceGetForTutorsStub.mockReturnValue(
                    of(new HttpResponse({ body: { ...modelingExercise, latestExamEndDate: assessmentPossibleFrom, assessmentPossibleFrom } as ModelingExercise })),
                );

                comp.loadAll();

                // a larger delay would overflow setTimeout's signed 32-bit delay and fire immediately, spinning the loop
                const delays = setTimeoutSpy.mock.calls.map((call) => call[1]);
                expect(delays).toContain(2_147_483_647);
                expect(Math.max(...(delays as number[]))).toBeLessThanOrEqual(2_147_483_647);
            } finally {
                setTimeoutSpy.mockRestore();
                vi.useRealTimers();
            }
        });

        it('should offer no explanation once assessment is possible', () => {
            exerciseServiceGetForTutorsStub.mockReturnValue(
                of(
                    new HttpResponse({
                        body: { ...modelingExercise, latestExamEndDate: dayjs().subtract(1, 'hour'), assessmentPossibleFrom: dayjs().subtract(1, 'hour') } as ModelingExercise,
                        headers: new HttpHeaders(),
                    }),
                ),
            );

            comp.loadAll();

            expect(comp.assessmentNotPossibleYetReason()).toBeUndefined();
            expect(comp.assessmentNotPossibleYetTooltip()).toBe('');
        });
    });
});
