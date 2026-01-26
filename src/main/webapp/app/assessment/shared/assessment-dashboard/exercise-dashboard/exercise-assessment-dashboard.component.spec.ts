import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { TutorParticipationDTO, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Language } from 'app/core/course/shared/entities/course.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercise/submission/submission.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ArtemisNavigationUtilService, getLinkToSubmissionAssessment } from 'app/shared/util/navigation.utils';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { SortService } from 'app/shared/service/sort.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseAssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { InfoPanelComponent } from 'app/assessment/shared/info-panel/info-panel.component';
import { SecondCorrectionEnableButtonComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/second-correction-button/second-correction-enable-button.component';
import { LanguageTableCellComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/language-table-cell/language-table-cell.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('ExerciseAssessmentDashboardComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: ExerciseAssessmentDashboardComponent;
    let fixture: ComponentFixture<ExerciseAssessmentDashboardComponent>;

    let modelingSubmissionService: ModelingSubmissionService;
    let modelingSubmissionStubWithAssessment: MockInstance;
    let modelingSubmissionStubWithoutAssessment: MockInstance;

    let textSubmissionService: TextSubmissionService;
    let textSubmissionStubWithAssessment: MockInstance;
    let textSubmissionStubWithoutAssessment: MockInstance;

    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadSubmissionStubWithAssessment: MockInstance;
    let fileUploadSubmissionStubWithoutAssessment: MockInstance;

    let programmingSubmissionService: ProgrammingSubmissionService;
    let programmingSubmissionStubWithAssessment: MockInstance;
    let programmingSubmissionStubWithoutAssessment: MockInstance;

    let exerciseService: ExerciseService;
    let exerciseServiceGetForTutorsStub: MockInstance;
    let exerciseServiceGetStatsForTutorsStub: MockInstance;

    let tutorParticipationService: TutorParticipationService;

    let accountService: AccountService;

    let translateService: TranslateService;

    let submissionService: SubmissionService;

    const result1 = { id: 11, correctionRound: 0 } as Result;
    const result2 = { id: 12, correctionRound: 1 } as Result;
    const exam = { id: 13, numberOfCorrectionRoundsInExam: 2 } as Exam;
    const exerciseGroup = { id: 14, exam } as ExerciseGroup;

    const exercise = {
        id: 15,
        exerciseGroup,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const programmingExercise = {
        id: 16,
        exerciseGroup,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as ProgrammingExercise;
    const programmingExerciseWithAutomaticAssessment = {
        id: 16,
        exerciseGroup,
        type: ExerciseType.PROGRAMMING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
        assessmentType: AssessmentType.AUTOMATIC,
        allowComplaintsForAutomaticAssessments: true,
    } as ProgrammingExercise;
    const modelingExercise = {
        id: 17,
        exerciseGroup,
        type: ExerciseType.MODELING,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        exampleSolutionModel: '{"elements": [{"id": 1}]}',
        exampleSolutionExplanation: 'explanation',
    } as ModelingExercise;
    const textExercise = {
        id: 18,
        exerciseGroup,
        type: ExerciseType.TEXT,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
        exampleParticipations: [
            { id: 1, usedForTutorial: false },
            { id: 2, usedForTutorial: true },
        ],
    } as TextExercise;
    const fileUploadExercise = {
        id: 19,
        exerciseGroup,
        type: ExerciseType.FILE_UPLOAD,
        tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }],
        secondCorrectionEnabled: false,
    } as FileUploadExercise;

    const participation = { id: 20, submissions: [] } as Participation;

    const modelingSubmission = { id: 21 } as ModelingSubmission;
    const fileUploadSubmission = { id: 22 } as FileUploadSubmission;
    const textSubmission = { id: 23, submissionExerciseType: SubmissionExerciseType.TEXT, language: Language.ENGLISH } as TextSubmission;
    const programmingSubmission = { id: 24 } as ProgrammingSubmission;

    const modelingSubmissionAssessed = { id: 25, results: [result1, result2], participation } as ModelingSubmission;
    const fileUploadSubmissionAssessed = { id: 26, results: [result1, result2], participation } as FileUploadSubmission;
    const textSubmissionAssessed = {
        id: 27,
        submissionExerciseType: SubmissionExerciseType.TEXT,
        language: Language.GERMAN,
        results: [result1, result2],
        participation,
    } as TextSubmission;
    const programmingSubmissionAssessed = { id: 28, results: [result1, result2], participation } as ProgrammingSubmission;

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
    const lockLimitErrorResponse = new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } });

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
        MockComponent(LanguageTableCellComponent),
        MockComponent(ProgrammingExerciseInstructionComponent),
        MockComponent(ButtonComponent),
        MockComponent(ResultComponent),
        MockDirective(ExtensionPointDirective),
        MockHasAnyAuthorityDirective,
        MockTranslateValuesDirective,
        MockComponent(AssessmentWarningComponent),
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
        provideHttpClient(),
        provideHttpClientTesting(),
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports,
            providers,
        })
            .overrideComponent(ExerciseAssessmentDashboardComponent, {
                remove: { imports: [TutorLeaderboardComponent] },
                add: { imports: [MockComponent(TutorLeaderboardComponent)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ExerciseAssessmentDashboardComponent);
        comp = fixture.componentInstance;
        modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
        textSubmissionService = TestBed.inject(TextSubmissionService);
        fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
        exerciseService = TestBed.inject(ExerciseService);
        programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
        submissionService = TestBed.inject(SubmissionService);
        vi.spyOn(submissionService, 'getSubmissionsWithComplaintsForTutor').mockReturnValue(of(new HttpResponse({ body: [] })));
        const router = fixture.debugElement.injector.get(Router);
        navigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
        tutorParticipationService = TestBed.inject(TutorParticipationService);
        exerciseServiceGetForTutorsStub = vi.spyOn(exerciseService, 'getForTutors');
        exerciseServiceGetStatsForTutorsStub = vi.spyOn(exerciseService, 'getStatsForTutors');
        exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: modelingExercise, headers: new HttpHeaders() })));
        exerciseServiceGetStatsForTutorsStub.mockReturnValue(of(new HttpResponse({ body: stats, headers: new HttpHeaders() })));
        comp.exerciseId = modelingExercise.id!;
        modelingSubmissionStubWithoutAssessment = vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment');
        modelingSubmissionStubWithAssessment = vi.spyOn(modelingSubmissionService, 'getSubmissions');
        textSubmissionStubWithoutAssessment = vi.spyOn(textSubmissionService, 'getSubmissionWithoutAssessment');
        textSubmissionStubWithAssessment = vi.spyOn(textSubmissionService, 'getSubmissions');
        fileUploadSubmissionStubWithoutAssessment = vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment');
        fileUploadSubmissionStubWithAssessment = vi.spyOn(fileUploadSubmissionService, 'getSubmissions');
        programmingSubmissionStubWithoutAssessment = vi.spyOn(programmingSubmissionService, 'getSubmissionWithoutAssessment');
        programmingSubmissionStubWithAssessment = vi.spyOn(programmingSubmissionService, 'getSubmissions');
        textSubmissionStubWithoutAssessment.mockReturnValue(of(textSubmission));
        textSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [textSubmissionAssessed], headers: new HttpHeaders() })));
        fileUploadSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [fileUploadSubmissionAssessed], headers: new HttpHeaders() })));
        fileUploadSubmissionStubWithoutAssessment.mockReturnValue(of(fileUploadSubmission));
        programmingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [programmingSubmissionAssessed], headers: new HttpHeaders() })));
        programmingSubmissionStubWithoutAssessment.mockReturnValue(of(programmingSubmission));
        modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [modelingSubmissionAssessed], headers: new HttpHeaders() })));
        modelingSubmissionStubWithoutAssessment.mockReturnValue(of(modelingSubmission));
        comp.submissionsWithComplaints = [submissionWithComplaintDTO];
        accountService = TestBed.inject(AccountService);
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const user = { id: 10, login: 'tutor1' } as User;
        accountService.userIdentity.set(user);
        fixture.detectChanges();

        expect(comp.courseId).toBe(1);
        expect(comp.examId).toBe(2);
        expect(comp.exerciseId).toBe(modelingExercise.id);

        expect(comp.tutor).toEqual(user);

        const setupGraphSpy = vi.spyOn(comp, 'setupGraph');

        translateService.use('en'); // Change language.
        expect(setupGraphSpy).toHaveBeenCalledTimes(1);
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

        expect(comp.numberOfTutorAssessments).toBe(tutorLeaderBoardEntry.numberOfAssessments);
        expect(comp.complaintsDashboardInfo.tutor).toBe(tutorLeaderBoardEntry.numberOfTutorComplaints);
        expect(comp.moreFeedbackRequestsDashboardInfo.tutor).toBe(tutorLeaderBoardEntry.numberOfTutorMoreFeedbackRequests);
        expect(comp.ratingsDashboardInfo.tutor).toBe(tutorLeaderBoardEntry.numberOfTutorRatings);

        const setupGraphSpy = vi.spyOn(comp, 'setupGraph');

        translateService.use('en'); // Change language.
        expect(setupGraphSpy).toHaveBeenCalledTimes(1);
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(1, modelingExercise.id, undefined, 0);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(2, modelingExercise.id, undefined, 1);

        expect(comp.unassessedSubmissionByRound?.get(0)).toEqual(modelingSubmission);
        expect(comp.unassessedSubmissionByRound?.get(0)?.latestResult).toBeUndefined();
        expect(comp.submissionLockLimitReached).toBe(false);
        expect(comp.assessedSubmissionsByRound?.get(0)).toHaveLength(0);
    });

    it('should not set unassessedSubmission if lock limit is reached', () => {
        modelingSubmissionStubWithoutAssessment.mockReturnValue(throwError(() => lockLimitErrorResponse));
        modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(1, modelingExercise.id, undefined, 0);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(2, modelingExercise.id, undefined, 1);

        expect(comp.unassessedSubmissionByRound?.get(1)).toBeUndefined();
        expect(comp.submissionLockLimitReached).toBe(true);
        expect(comp.assessedSubmissionsByRound?.get(1)).toHaveLength(0);
    });

    it('should handle if no more submissions are assessable', () => {
        comp.unassessedSubmissionByRound = new Map<number, Submission>();
        comp.unassessedSubmissionByRound.set(0, modelingSubmission);
        comp.unassessedSubmissionByRound.set(1, modelingSubmission);

        modelingSubmissionStubWithoutAssessment.mockReturnValue(of(undefined));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        expect(comp.unassessedSubmissionByRound.get(0)).toBeUndefined();
        expect(comp.unassessedSubmissionByRound.get(1)).toBeUndefined();
    });

    it('should handle generic error', () => {
        const error = { errorKey: 'mock', detail: 'Mock error' };
        const errorResponse = new HttpErrorResponse({ error });

        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = vi.spyOn(alertService, 'error');

        modelingSubmissionStubWithoutAssessment.mockReturnValue(throwError(() => errorResponse));
        modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(alertServiceSpy).toHaveBeenCalledTimes(2);
        expect(alertServiceSpy).toHaveBeenNthCalledWith(1, error.detail);
        expect(alertServiceSpy).toHaveBeenNthCalledWith(2, error.detail);
    });

    it('should have correct percentages calculated', () => {
        modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(1, modelingExercise.id, undefined, 0);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(2, modelingExercise.id, undefined, 1);

        expect(comp.numberOfAssessmentsOfCorrectionRounds[0].inTime).toBe(1);
        expect(comp.numberOfAssessmentsOfCorrectionRounds[1].inTime).toBe(8);
        expect(comp.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound[0].inTime).toBe(2);
        expect(comp.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound[1].inTime).toBe(7);
        expect(comp.assessedSubmissionsByRound?.get(1)).toHaveLength(0);
    });

    it('should  set assessed Submission and latest result', () => {
        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        expect(comp.assessedSubmissionsByRound?.get(1)![0]).toEqual(modelingSubmissionAssessed);
        expect(comp.assessedSubmissionsByRound?.get(1)![0]?.participation!.submissions![0]).toEqual(comp.assessedSubmissionsByRound?.get(1)![0]);
        expect(comp.assessedSubmissionsByRound?.get(1)![0]?.latestResult).toEqual(result2);
    });

    it('should set exam and stats properties', () => {
        expect(comp.exam).toBeUndefined();

        comp.loadAll();
        expect(comp.exercise.id).toBe(modelingExercise.id);
        expect(comp.exam).toEqual(exam);
        expect(comp.exam?.numberOfCorrectionRoundsInExam).toBe(numberOfAssessmentsOfCorrectionRounds.length);
        expect(comp.numberOfAssessmentsOfCorrectionRounds).toEqual(numberOfAssessmentsOfCorrectionRounds);
    });

    it('should calculateStatus DRAFT', () => {
        expect(modelingSubmission.latestResult).toBeUndefined();
        expect(comp.calculateSubmissionStatusIsDraft(modelingSubmission)).toBe(true);
    });

    it('should call hasBeenCompletedByTutor', () => {
        comp.exampleParticipationsCompletedByTutor = [{ id: 1 }, { id: 2 }];
        expect(comp.hasBeenCompletedByTutor(1)).toBe(true);
    });

    it('should call readInstruction', () => {
        const tutorParticipationServiceCreateStub = vi.spyOn(tutorParticipationService, 'create');
        const dto: TutorParticipationDTO = {
            id: 1,
            exerciseId: comp.exerciseId,
            tutorId: 2,
            status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS,
        };

        tutorParticipationServiceCreateStub.mockImplementation(() => {
            expect(comp.isLoading).toBe(true);
            return of(new HttpResponse({ body: dto, headers: new HttpHeaders() }));
        });

        expect(comp.tutorParticipation).toBeUndefined();
        expect(comp.isLoading).toBe(false);

        comp.readInstruction();

        expect(tutorParticipationServiceCreateStub).toHaveBeenCalledTimes(1);
        expect(tutorParticipationServiceCreateStub).toHaveBeenCalledWith(comp.exerciseId);

        expect(comp.isLoading).toBe(false);

        expect(comp.tutorParticipation).toEqual(dto);
        expect(comp.tutorParticipationStatus).toEqual(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
    });

    describe('test calls for all exercise types', () => {
        it('fileuploadSubmission', () => {
            modelingSubmissionStubWithoutAssessment.mockReturnValue(throwError(() => lockLimitErrorResponse));
            modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: fileUploadExercise, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(fileUploadSubmissionStubWithAssessment).toHaveBeenCalledTimes(2);
            expect(fileUploadSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        });

        it('textSubmission', () => {
            modelingSubmissionStubWithoutAssessment.mockReturnValue(throwError(() => lockLimitErrorResponse));

            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: textExercise, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(textSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
            expect(textSubmissionStubWithAssessment).toHaveBeenCalledTimes(2);

            expect(comp.exampleParticipationsToReview).toHaveLength(1);
            expect(comp.exampleParticipationsToReview[0]).toEqual(textExercise.exampleParticipations![0]);

            expect(comp.exampleParticipationsToAssess).toHaveLength(1);
            expect(comp.exampleParticipationsToAssess[0]).toEqual(textExercise.exampleParticipations![1]);
        });

        it('programmingSubmission', () => {
            modelingSubmissionStubWithoutAssessment.mockReturnValue(throwError(() => lockLimitErrorResponse));

            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: programmingExercise, headers: new HttpHeaders() })));

            comp.loadAll();

            expect(programmingSubmissionStubWithAssessment).toHaveBeenCalledTimes(2);
            expect(programmingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        });

        it('programmingSubmission with automatic assessment', () => {
            modelingSubmissionStubWithoutAssessment.mockReturnValue(throwError(() => lockLimitErrorResponse));

            exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: programmingExerciseWithAutomaticAssessment, headers: new HttpHeaders() })));

            const translateServiceSpy = vi.spyOn(translateService, 'instant');

            comp.loadAll();

            expect(programmingSubmissionStubWithAssessment).toHaveBeenCalledTimes(2);
            expect(programmingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);

            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.exerciseAssessmentDashboard.numberOfOpenComplaints');
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.exerciseAssessmentDashboard.numberOfResolvedComplaints');
        });
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
            comp.exercise = {
                allowFeedbackRequests: false,
                type: fakeExerciseType,
                numberOfAssessmentsOfCorrectionRounds: [],
                studentAssignedTeamIdComputed: false,
                secondCorrectionEnabled: false,
            };
            comp.courseId = fakeCourseId;
            comp.exerciseId = fakeExerciseId;
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
            comp.numberOfAssessmentsOfCorrectionRounds = new Array(arrayLength);
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
            comp.submissionsWithComplaints = [{ submission, complaint: complaintComplaint }];
            const complaintQuery = comp.getComplaintQueryParams(complaintComplaint);

            expect(complaintQuery).toEqual(comp.getAssessmentQueryParams(fakeResults.length - 1));
        });
    });

    describe('getSubmissionToViewFromComplaintSubmission', () => {
        it('Expect not present submission to resolve undefined', () => {
            const fakeDTOList: SubmissionWithComplaintDTO[] = [];
            const inputSubmission = { id: 1 };
            comp.submissionsWithComplaints = fakeDTOList;
            const submissionToView = comp.getSubmissionToViewFromComplaintSubmission(inputSubmission);

            expect(submissionToView).toBeUndefined();
        });

        it('Expect submission without results to gain an empty list', () => {
            const fakeDTOList: SubmissionWithComplaintDTO[] = [{ submission: { id: 1 }, complaint: {} }];
            const expectedSubmission = { id: 1, results: [] };
            const inputSubmission = { id: 1 };
            comp.submissionsWithComplaints = fakeDTOList;
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
            comp.submissionsWithComplaints = fakeDTOList;
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
            comp.exercise = exercise;
            comp.exercise.type = ExerciseType.PROGRAMMING;
            comp.courseId = 4;
            comp.exercise = exercise;
            const submission = { id: 8 };
            comp.openExampleSubmission(submission!.id, true, true);
            expect(navigateSpy).toHaveBeenCalledWith([`/course-management/${courseId}/${exercise.type}-exercises/${exercise.id}/example-submissions/${submission.id}`], {
                queryParams: { readOnly: true, toComplete: true },
            });
        });
    });

    it('generate exercise detail link', () => {
        comp.exercise = modelingExercise;
        comp.courseId = 4;
        const exerciseDetailsLink = comp.getExerciseDetailsLink();
        expect(exerciseDetailsLink).toEqual(['/course-management', 4, ExerciseType.MODELING + '-exercises', modelingExercise.id]);
    });

    it('should toggle second correction', () => {
        comp.exercise = exercise;
        comp.exercise.type = ExerciseType.TEXT;

        const secondCorrectionEnabled = true;
        vi.spyOn(exerciseService, 'toggleSecondCorrection').mockImplementation((exerciseId) => {
            expect(comp.togglingSecondCorrectionButton).toBe(secondCorrectionEnabled);

            expect(exerciseId).toBe(comp.exerciseId);
            return of(secondCorrectionEnabled);
        });

        comp.toggleSecondCorrection();

        expect(comp.togglingSecondCorrectionButton).toBe(false);
        expect(comp.secondCorrectionEnabled).toBe(secondCorrectionEnabled);
        expect(comp.numberOfCorrectionRoundsEnabled).toBe(2);
    });

    it('should check if complaint locked', () => {
        comp.exercise = exercise;
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
        expect(sortServicePropertySpy).toHaveBeenNthCalledWith(1, comp.submissionsWithMoreFeedbackRequests, 'complaint.submittedTime', true);
        expect(sortServicePropertySpy).toHaveBeenNthCalledWith(2, comp.submissionsWithMoreFeedbackRequests, 'complaint.accepted', false);

        comp.sortPredicates[2] = 'responseTime';
        const sortServiceFunctionSpy = vi.spyOn(sortService, 'sortByFunction');

        comp.sortMoreFeedbackRows();

        expect(sortServiceFunctionSpy).toHaveBeenCalledWith(comp.submissionsWithMoreFeedbackRequests, expect.any(Function), false);
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
});
