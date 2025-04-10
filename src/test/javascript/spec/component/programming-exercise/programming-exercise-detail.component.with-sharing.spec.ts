import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProgrammingExerciseDetailComponent } from 'app/programming/manage/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/entities/course.model';
import { TranslateModule } from '@ngx-translate/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { Exam } from 'app/entities/exam/exam.model';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProvider } from 'ng-mocks';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockProgrammingExerciseGradingService } from '../../helpers/mocks/service/mock-programming-exercise-grading.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProgrammingLanguageFeature, ProgrammingLanguageFeatureService } from 'app/programming/service/programming-language-feature/programming-language-feature.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/programming-exercise-git-diff-report.model';
import { BuildLogStatisticsDTO } from 'app/entities/programming/build-log-statistics-dto';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

/*
 * just use a separate file for sharing aspects, could be merged into programming-exercise-detail.component.spec.ts if stabilized.
 */
describe('ProgrammingExerciseDetailComponent', () => {
    let comp: ProgrammingExerciseDetailComponent;
    let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
    let statisticsService: StatisticsService;
    let exerciseService: ProgrammingExerciseService;
    let alertService: AlertService;
    let profileService: ProfileService;
    let submissionPolicyService: SubmissionPolicyService;
    let programmingLanguageFeatureService: ProgrammingLanguageFeatureService;
    let statisticsServiceStub: jest.SpyInstance;
    let gitDiffReportStub: jest.SpyInstance;
    let profileServiceStub: jest.SpyInstance;
    let submissionPolicyServiceStub: jest.SpyInstance;
    let buildLogStatisticsStub: jest.SpyInstance;
    let findWithTemplateAndSolutionParticipationStub: jest.SpyInstance;
    let router: Router;

    const mockProgrammingExercise = {
        id: 1,
        categories: [{ category: 'Important' }],
        templateParticipation: {
            id: 1,
        } as TemplateProgrammingExerciseParticipation,
        solutionParticipation: {
            id: 2,
        } as SolutionProgrammingExerciseParticipation,
    } as ProgrammingExercise;

    const exerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    const gitDiffReport = {
        templateRepositoryCommitHash: 'x1',
        solutionRepositoryCommitHash: 'x2',
        entries: [
            {
                previousFilePath: '/src/test.java',
                filePath: '/src/test.java',
                previousStartLine: 1,
                startLine: 1,
                previousLineCount: 2,
                lineCount: 2,
            },
        ],
    } as ProgrammingExerciseGitDiffReport;

    const buildLogStatistics = {
        buildCount: 5,
        agentSetupDuration: 2.5,
        testDuration: 3,
        scaDuration: 2,
        totalJobDuration: 7.5,
        dependenciesDownloadedCount: 6,
    } as BuildLogStatisticsDTO;

    const profileInfo = {
        activeProfiles: [],
    } as unknown as ProfileInfo;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            providers: [
                MockProvider(AlertService),
                MockProvider(ProgrammingLanguageFeatureService),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useValue: new MockProfileService() },
                { provide: ProgrammingExerciseGradingService, useValue: new MockProgrammingExerciseGradingService() },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: NgbModal, useValue: new MockNgbModalService() },
                { provide: Router, useClass: MockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseDetailComponent);
        comp = fixture.componentInstance;

        statisticsService = fixture.debugElement.injector.get(StatisticsService);
        statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
        alertService = fixture.debugElement.injector.get(AlertService);
        exerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        profileService = fixture.debugElement.injector.get(ProfileService);
        submissionPolicyService = fixture.debugElement.injector.get(SubmissionPolicyService);

        programmingLanguageFeatureService = fixture.debugElement.injector.get(ProgrammingLanguageFeatureService);
        router = fixture.debugElement.injector.get(Router);

        findWithTemplateAndSolutionParticipationStub = jest
            .spyOn(exerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValue(of(new HttpResponse<ProgrammingExercise>({ body: mockProgrammingExercise })));
        gitDiffReportStub = jest.spyOn(exerciseService, 'getDiffReport').mockReturnValue(of(gitDiffReport));
        profileServiceStub = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(profileInfo));
        submissionPolicyServiceStub = jest.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));
        buildLogStatisticsStub = jest.spyOn(exerciseService, 'getBuildLogStatistics').mockReturnValue(of(buildLogStatistics));

        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(profileInfo));
        jest.spyOn(programmingLanguageFeatureService, 'getProgrammingLanguageFeature').mockReturnValue({
            plagiarismCheckSupported: true,
        } as ProgrammingLanguageFeature);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('onInit for sharing import exercise', () => {
        let httpMock: HttpTestingController;
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ programmingExercise });
            httpMock = TestBed.inject(HttpTestingController);
        });
        afterEach(() => {
            //            httpMock.verify();
        });

        it('should be in sharing mode', async () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            const req = httpMock.expectOne({ method: 'GET', url: 'api/sharing/config/isEnabled' });
            req.flush(true);

            // THEN
            expect(comp.isExportToSharingEnabled).toBeTruthy();
        });

        it('should not be in sharing mode', async () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            const req = httpMock.expectOne({ method: 'GET', url: 'api/sharing/config/isEnabled' });
            req.flush(
                { message: 'Resource not found' }, // error body
                {
                    status: 404,
                    statusText: 'Not Found',
                },
            );

            // THEN
            expect(comp.isExportToSharingEnabled).toBeFalsy();
        });
    });
});
