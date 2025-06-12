import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExerciseDetailComponent } from 'app/programming/manage/detail/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateModule } from '@ngx-translate/core';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockProgrammingExerciseGradingService } from 'test/helpers/mocks/service/mock-programming-exercise-grading.service';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { ProfileInfo, ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProgrammingExerciseGitDiffReport } from 'app/programming/shared/entities/programming-exercise-git-diff-report.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';

/*
 * just use a separate file for sharing aspects, could be merged into programming-exercise-detail.component.spec.ts if stabilized.
 */
describe('ProgrammingExerciseDetailComponent', () => {
    let comp: ProgrammingExerciseDetailComponent;
    let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
    let statisticsService: StatisticsService;
    let exerciseService: ProgrammingExerciseService;
    let profileService: ProfileService;
    let submissionPolicyService: SubmissionPolicyService;
    let programmingLanguageFeatureService: ProgrammingLanguageFeatureService;

    const mockProgrammingExercise = {
        id: 1,
        categories: [{ category: 'Important' }],
        templateParticipation: {
            id: 1,
        } as TemplateProgrammingExerciseParticipation,
        solutionParticipation: {
            id: 2,
        } as SolutionProgrammingExerciseParticipation,
        buildConfig: {
            buildTool: 'GRADLE',
            buildImage: 'ghcr.io/ls1intum/gradle-jdk17:latest',
            buildImagePullSecret: 'gradle-jdk17-pull-secret',
            buildImagePullSecretName: 'gradle-jdk17-pull-secret',
        },
    } as unknown as ProgrammingExercise;

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

    const profileInfo = {
        activeProfiles: [],
        activeModuleFeatures: [MODULE_FEATURE_PLAGIARISM],
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
        jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
        exerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        profileService = fixture.debugElement.injector.get(ProfileService);
        submissionPolicyService = fixture.debugElement.injector.get(SubmissionPolicyService);

        programmingLanguageFeatureService = fixture.debugElement.injector.get(ProgrammingLanguageFeatureService);

        jest.spyOn(exerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: mockProgrammingExercise })),
        );
        jest.spyOn(exerciseService, 'getDiffReport').mockReturnValue(of(gitDiffReport));
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
        jest.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));

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
            httpMock.verify();
        });

        it('should be in sharing mode', async () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            const req = httpMock.expectOne({ method: 'GET', url: 'api/core/sharing/config/is-enabled' });
            req.flush(true);

            // THEN
            expect(comp.isExportToSharingEnabled).toBeTruthy();
        });

        it('should not be in sharing mode', async () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            const req = httpMock.expectOne({ method: 'GET', url: 'api/core/sharing/config/is-enabled' });
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

        it('should not be in sharing mode because profile enabled but body empty', async () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            const req = httpMock.expectOne({ method: 'GET', url: 'api/core/sharing/config/is-enabled' });
            req.flush(
                null, // empty body
                {
                    status: 200,
                    statusText: 'OK',
                },
            );

            // THEN
            expect(comp.isExportToSharingEnabled).toBeFalsy();
        });
    });
});
