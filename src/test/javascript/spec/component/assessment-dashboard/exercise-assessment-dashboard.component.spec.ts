import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Language } from 'app/entities/course.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';
import { LanguageTableCellComponent } from 'app/exercises/shared/dashboards/tutor/language-table-cell/language-table-cell.component';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercises/shared/submission/submission.service';
import { InfoPanelComponent } from 'app/shared/info-panel/info-panel.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { ComplaintService } from 'app/complaints/complaint.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ArtemisNavigationUtilService, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { PieChartModule } from '@swimlane/ngx-charts';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { RouterTestingModule } from '@angular/router/testing';
import { SortService } from 'app/shared/service/sort.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';

describe('ExerciseAssessmentDashboardComponent', () => {
    let comp: ExerciseAssessmentDashboardComponent;
    let fixture: ComponentFixture<ExerciseAssessmentDashboardComponent>;

    let modelingSubmissionService: ModelingSubmissionService;
    let modelingSubmissionStubWithAssessment: jest.SpyInstance;
    let modelingSubmissionStubWithoutAssessment: jest.SpyInstance;

    let textSubmissionService: TextSubmissionService;
    let textSubmissionStubWithAssessment: jest.SpyInstance;
    let textSubmissionStubWithoutAssessment: jest.SpyInstance;

    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadSubmissionStubWithAssessment: jest.SpyInstance;
    let fileUploadSubmissionStubWithoutAssessment: jest.SpyInstance;

    let programmingSubmissionService: ProgrammingSubmissionService;
    let programmingSubmissionStubWithAssessment: jest.SpyInstance;
    let programmingSubmissionStubWithoutAssessment: jest.SpyInstance;

    let exerciseService: ExerciseService;
    let exerciseServiceGetForTutorsStub: jest.SpyInstance;
    let exerciseServiceGetStatsForTutorsStub: jest.SpyInstance;

    let tutorParticipationService: TutorParticipationService;

    let guidedTourService: GuidedTourService;

    let accountService: AccountService;

    let translateService: TranslateService;

    let submissionService: SubmissionService;

    const result1 = { id: 11 } as Result;
    const result2 = { id: 12 } as Result;
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
        exampleSubmissions: [
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
        totalNumberOfAssessments: { inTime: 9, late: 1 },
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

    let navigateSpy: jest.SpyInstance;
    let routingStub: jest.SpyInstance;
    const route = {
        snapshot: {
            paramMap: convertToParamMap({
                courseId: 1,
                examId: 2,
                exerciseGroupId: 3,
                exerciseId: modelingExercise.id!,
            }),
        },
    } as any as ActivatedRoute;
    const imports = [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(PieChartModule)];

    const declarations = [
        ExerciseAssessmentDashboardComponent,
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
        MockDirective(NgbTooltip),
        MockComponent(AssessmentWarningComponent),
        MockPipe(ArtemisDatePipe),
        MockPipe(ArtemisTranslatePipe),
    ];

    const providers = [
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: AccountService, useClass: MockAccountService },
        { provide: ActivatedRoute, useValue: route },
        { provide: LocalStorageService, useClass: MockSyncStorage },
        { provide: SessionStorageService, useClass: MockSyncStorage },
        MockProvider(ExerciseService),
        MockProvider(AlertService),
        MockProvider(TutorParticipationService),
        MockProvider(ArtemisMarkdownService),
        MockProvider(ComplaintService),
        MockProvider(GuidedTourService),
        MockProvider(ArtemisDatePipe),
        MockProvider(SortService),
        MockProvider(ArtemisNavigationUtilService),
    ];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports,
            declarations,
            providers,
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseAssessmentDashboardComponent);
                comp = fixture.componentInstance;

                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
                exerciseService = TestBed.inject(ExerciseService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);

                submissionService = TestBed.inject(SubmissionService);
                jest.spyOn(submissionService, 'getSubmissionsWithComplaintsForTutor').mockReturnValue(of(new HttpResponse({ body: [] })));

                const complaintService = TestBed.inject(ComplaintService);
                jest.spyOn(complaintService, 'getMoreFeedbackRequestsForTutor').mockReturnValue(of(new HttpResponse({ body: [] })));

                const router = fixture.debugElement.injector.get(Router);
                navigateSpy = jest.spyOn(router, 'navigate').mockImplementation();

                tutorParticipationService = TestBed.inject(TutorParticipationService);

                exerciseServiceGetForTutorsStub = jest.spyOn(exerciseService, 'getForTutors');
                exerciseServiceGetStatsForTutorsStub = jest.spyOn(exerciseService, 'getStatsForTutors');

                exerciseServiceGetForTutorsStub.mockReturnValue(of(new HttpResponse({ body: modelingExercise, headers: new HttpHeaders() })));
                exerciseServiceGetStatsForTutorsStub.mockReturnValue(of(new HttpResponse({ body: stats, headers: new HttpHeaders() })));

                guidedTourService = TestBed.inject(GuidedTourService);

                comp.exerciseId = modelingExercise.id!;

                modelingSubmissionStubWithoutAssessment = jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment');
                modelingSubmissionStubWithAssessment = jest.spyOn(modelingSubmissionService, 'getSubmissions');

                textSubmissionStubWithoutAssessment = jest.spyOn(textSubmissionService, 'getSubmissionWithoutAssessment');
                textSubmissionStubWithAssessment = jest.spyOn(textSubmissionService, 'getSubmissions');

                fileUploadSubmissionStubWithoutAssessment = jest.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment');
                fileUploadSubmissionStubWithAssessment = jest.spyOn(fileUploadSubmissionService, 'getSubmissions');

                programmingSubmissionStubWithoutAssessment = jest.spyOn(programmingSubmissionService, 'getSubmissionWithoutAssessment');
                programmingSubmissionStubWithAssessment = jest.spyOn(programmingSubmissionService, 'getSubmissions');

                textSubmissionStubWithoutAssessment.mockReturnValue(of(textSubmission));
                textSubmissionStubWithAssessment.mockReturnValue(of(textSubmissionAssessed));

                fileUploadSubmissionStubWithAssessment.mockReturnValue(of(fileUploadSubmissionAssessed));
                fileUploadSubmissionStubWithoutAssessment.mockReturnValue(of(fileUploadSubmission));

                programmingSubmissionStubWithAssessment.mockReturnValue(of(programmingSubmissionAssessed));
                programmingSubmissionStubWithoutAssessment.mockReturnValue(of(programmingSubmission));

                modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [modelingSubmissionAssessed], headers: new HttpHeaders() })));
                modelingSubmissionStubWithoutAssessment.mockReturnValue(of(modelingSubmission));
                comp.submissionsWithComplaints = [submissionWithComplaintDTO];

                accountService = TestBed.inject(AccountService);
                const navigationUtilService = TestBed.inject(ArtemisNavigationUtilService);

                routingStub = jest.spyOn(navigationUtilService, 'routeInNewTab');

                translateService = TestBed.inject(TranslateService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        const user = { id: 10 } as User;
        jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

        fixture.detectChanges();

        expect(comp.courseId).toBe(1);
        expect(comp.examId).toBe(2);
        expect(comp.exerciseGroupId).toBe(3);
        expect(comp.exerciseId).toBe(modelingExercise.id);

        tick();

        expect(comp.tutor).toEqual(user);

        const setupGraphSpy = jest.spyOn(comp, 'setupGraph');

        translateService.use('en'); // Change language.
        expect(setupGraphSpy).toHaveBeenCalledOnce();
    }));

    it('should initialize with tutor leaderboard entry', () => {
        const tutor = { id: 10 } as User;
        comp.tutor = tutor;
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

        const setupGraphSpy = jest.spyOn(comp, 'setupGraph');

        translateService.use('en'); // Change language.
        expect(setupGraphSpy).toHaveBeenCalledOnce();
    });

    it('should set unassessedSubmission if lock limit is not reached', () => {
        const guidedTourMapping = {} as GuidedTourMapping;
        jest.spyOn<any, any>(guidedTourService, 'checkTourState').mockReturnValue(true);
        guidedTourService.guidedTourMapping = guidedTourMapping;
        modelingSubmissionStubWithAssessment.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));

        comp.loadAll();

        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenCalledTimes(2);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(1, modelingExercise.id, undefined, 0);
        expect(modelingSubmissionStubWithoutAssessment).toHaveBeenNthCalledWith(2, modelingExercise.id, undefined, 1);

        expect(comp.unassessedSubmissionByRound?.get(0)).toEqual(modelingSubmission);
        expect(comp.unassessedSubmissionByRound?.get(0)?.latestResult).toBeUndefined();
        expect(comp.submissionLockLimitReached).toBeFalse();
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
        expect(comp.submissionLockLimitReached).toBeTrue();
        expect(comp.assessedSubmissionsByRound?.get(1)).toHaveLength(0);
    });

    it('should handle generic error', () => {
        const error = { errorKey: 'mock', detail: 'Mock error' };
        const errorResponse = new HttpErrorResponse({ error });

        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'error');

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
        expect(comp.calculateSubmissionStatusIsDraft(modelingSubmission)).toBeTrue();
    });

    it('should call hasBeenCompletedByTutor', () => {
        comp.exampleSubmissionsCompletedByTutor = [{ id: 1 }, { id: 2 }];
        expect(comp.hasBeenCompletedByTutor(1)).toBeTrue();
    });

    it('should call readInstruction', () => {
        const tutorParticipationServiceCreateStub = jest.spyOn(tutorParticipationService, 'create');
        const tutorParticipation = { id: 1, status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS };
        tutorParticipationServiceCreateStub.mockImplementation(() => {
            expect(comp.isLoading).toBeTrue();
            return of(new HttpResponse({ body: tutorParticipation, headers: new HttpHeaders() }));
        });

        expect(comp.tutorParticipation).toBeUndefined();
        expect(comp.isLoading).toBeFalse();

        comp.readInstruction();

        expect(comp.isLoading).toBeFalse();

        expect(comp.tutorParticipation).toEqual(tutorParticipation);
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

            expect(comp.exampleSubmissionsToReview).toHaveLength(1);
            expect(comp.exampleSubmissionsToReview[0]).toEqual(textExercise.exampleSubmissions![0]);

            expect(comp.exampleSubmissionsToAssess).toHaveLength(1);
            expect(comp.exampleSubmissionsToAssess[0]).toEqual(textExercise.exampleSubmissions![1]);
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

            const translateServiceSpy = jest.spyOn(translateService, 'instant');

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
                allowManualFeedbackRequests: false,
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

    describe('pie chart interaction', () => {
        let event: any;

        it('should not navigate if user is not instructor', () => {
            jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);
            event = { value: 60 };

            comp.navigateToExerciseSubmissionOverview(event);

            expect(routingStub).not.toHaveBeenCalled();
        });

        it('should navigate if user is instructor but clicked the chart legend', () => {
            event = 'test';

            assertRoutingPerformed();
        });

        it('should navigate if user is instructor and clicked pie part', () => {
            event = { name: 'test', value: 40 };

            assertRoutingPerformed();
        });
        const assertRoutingPerformed = () => {
            jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
            const exercises = [programmingExercise, modelingExercise, textExercise, fileUploadExercise];
            comp.assessments = [event];

            exercises.forEach((preparedExercise) => {
                comp.exercise = preparedExercise;
                comp.exerciseId = preparedExercise.id!;
                comp.courseId = 42;

                comp.navigateToExerciseSubmissionOverview(event);

                expect(routingStub).toHaveBeenCalledWith(['course-management', 42, preparedExercise.type + '-exercises', preparedExercise.id, 'submissions'], {
                    queryParams: { filterOption: 0 },
                });
            });
        };
    });

    it('should toggle second correction', () => {
        comp.exercise = exercise;
        comp.exercise.type = ExerciseType.TEXT;

        const secondCorrectionEnabled = true;
        jest.spyOn(exerciseService, 'toggleSecondCorrection').mockImplementation((exerciseId) => {
            expect(comp.togglingSecondCorrectionButton).toBe(secondCorrectionEnabled);

            expect(exerciseId).toBe(comp.exerciseId);
            return of(secondCorrectionEnabled);
        });

        comp.toggleSecondCorrection();

        expect(comp.togglingSecondCorrectionButton).toBeFalse();
        expect(comp.secondCorrectionEnabled).toBe(secondCorrectionEnabled);
        expect(comp.numberOfCorrectionRoundsEnabled).toBe(2);
    });

    it('should check if complaint locked', () => {
        comp.exercise = exercise;
        const complaintService = TestBed.inject(ComplaintService);
        const complaintServiceSpy = jest.spyOn(complaintService, 'isComplaintLockedForLoggedInUser');

        const complaint: Complaint = { id: 20 };
        comp.isComplaintLocked(complaint);

        expect(complaintServiceSpy).toHaveBeenCalledWith(complaint, exercise);
    });

    it('should get submissions with more feedback requests for tutor', () => {
        const submissionServiceSpy = jest.spyOn(submissionService, 'getSubmissionsWithMoreFeedbackRequestsForTutor');
        submissionServiceSpy.mockReturnValue(of(new HttpResponse({ body: [] })));

        const sortMoreFeedbackRowsSpy = jest.spyOn(comp, 'sortMoreFeedbackRows');

        fixture.detectChanges();

        expect(sortMoreFeedbackRowsSpy).toHaveBeenCalledOnce();

        submissionServiceSpy.mockReturnValue(throwError(() => errorResponse));

        const errorStatus = 400;
        const errorResponse = new HttpErrorResponse({ status: errorStatus });

        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'error');

        comp.loadAll();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('error.http.400');
    });

    it('should sort more feedback rows', () => {
        const sortService = TestBed.inject(SortService);
        const sortServicePropertySpy = jest.spyOn(sortService, 'sortByProperty');

        comp.sortMoreFeedbackRows();

        expect(sortServicePropertySpy).toHaveBeenCalledTimes(2);
        expect(sortServicePropertySpy).toHaveBeenNthCalledWith(1, comp.submissionsWithMoreFeedbackRequests, 'complaint.submittedTime', true);
        expect(sortServicePropertySpy).toHaveBeenNthCalledWith(2, comp.submissionsWithMoreFeedbackRequests, 'complaint.accepted', false);

        comp.sortPredicates[2] = 'responseTime';
        const sortServiceFunctionSpy = jest.spyOn(sortService, 'sortByFunction');

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
