import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { ProgrammingExerciseDetailComponent } from 'app/programming/manage/detail/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateModule } from '@ngx-translate/core';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProvider } from 'ng-mocks';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProgrammingExerciseGradingService } from 'test/helpers/mocks/service/mock-programming-exercise-grading.service';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileInfo, ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { HttpHeaders } from '@angular/common/http';

// Mock the diff.utils module to avoid Monaco Editor issues in tests
jest.mock('app/programming/shared/utils/diff.utils', () => ({
    ...jest.requireActual('app/programming/shared/utils/diff.utils'),
    processRepositoryDiff: jest.fn().mockImplementation((templateFiles, solutionFiles) => {
        // Handle the case where files are undefined (when repository fetch fails)
        if (!templateFiles || !solutionFiles) {
            return Promise.resolve(undefined);
        }
        return Promise.resolve({
            diffInformations: [
                {
                    originalFileContent: 'testing line differences',
                    modifiedFileContent: 'testing line diff\nnew line',
                    originalPath: 'Example.java',
                    modifiedPath: 'Example.java',
                    diffReady: true,
                    fileStatus: 'unchanged',
                    lineChange: {
                        addedLineCount: 2,
                        removedLineCount: 1,
                    },
                    title: 'Example.java',
                },
            ],
            totalLineChange: {
                addedLineCount: 2,
                removedLineCount: 1,
            },
        } as RepositoryDiffInformation);
    }),
}));

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
    let submissionPolicyServiceStub: jest.SpyInstance;
    let findWithTemplateAndSolutionParticipationStub: jest.SpyInstance;
    let getTemplateRepositoryFilesStub: jest.SpyInstance;
    let getSolutionRepositoryFilesStub: jest.SpyInstance;
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

    const mockDiffInformation = {
        diffInformations: [
            {
                originalFileContent: 'testing line differences',
                modifiedFileContent: 'testing line diff\nnew line',
                originalPath: 'Example.java',
                modifiedPath: 'Example.java',
                diffReady: false,
                fileStatus: 'unchanged',
                lineChange: {
                    addedLineCount: 2,
                    removedLineCount: 1,
                },
                title: 'Example.java',
            },
        ],
        totalLineChange: {
            addedLineCount: 2,
            removedLineCount: 1,
        },
    } as unknown as RepositoryDiffInformation;

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
                LocalStorageService,
                SessionStorageService,
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

        // Mock the ResizeObserver, which is not available in the test environment
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        fixture = TestBed.createComponent(ProgrammingExerciseDetailComponent);
        comp = fixture.componentInstance;

        statisticsService = TestBed.inject(StatisticsService);
        statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
        alertService = TestBed.inject(AlertService);
        exerciseService = TestBed.inject(ProgrammingExerciseService);
        profileService = TestBed.inject(ProfileService);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);

        programmingLanguageFeatureService = TestBed.inject(ProgrammingLanguageFeatureService);
        router = TestBed.inject(Router);

        findWithTemplateAndSolutionParticipationStub = jest
            .spyOn(exerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValue(of(new HttpResponse<ProgrammingExercise>({ body: mockProgrammingExercise })));
        getTemplateRepositoryFilesStub = jest
            .spyOn(exerciseService, 'getTemplateRepositoryTestFilesWithContent')
            .mockReturnValue(of(new Map([[mockDiffInformation.diffInformations[0].originalPath, mockDiffInformation.diffInformations[0].originalFileContent ?? '']])));
        getSolutionRepositoryFilesStub = jest
            .spyOn(exerciseService, 'getSolutionRepositoryTestFilesWithContent')
            .mockReturnValue(of(new Map([[mockDiffInformation.diffInformations[0].modifiedPath, mockDiffInformation.diffInformations[0].modifiedFileContent ?? '']])));
        submissionPolicyServiceStub = jest.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));

        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
        jest.spyOn(programmingLanguageFeatureService, 'getProgrammingLanguageFeature').mockReturnValue({
            plagiarismCheckSupported: true,
        } as ProgrammingLanguageFeature);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should reload on participation change', fakeAsync(() => {
        const fetchRepositoryFilesSpy = jest.spyOn(comp, 'fetchRepositoryFiles');
        jest.spyOn(exerciseService, 'getLatestResult').mockReturnValue({ successful: true });
        comp.programmingExercise = mockProgrammingExercise;
        comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;
        comp.onParticipationChange();
        tick();
        expect(fetchRepositoryFilesSpy).toHaveBeenCalledOnce();
        expect(getTemplateRepositoryFilesStub).toHaveBeenCalledOnce();
        expect(getSolutionRepositoryFilesStub).toHaveBeenCalledOnce();
    }));

    describe('onInit for course exercise', () => {
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ programmingExercise });
        });

        it('should not be in exam mode', async () => {
            // WHEN
            comp.ngOnInit();

            // THEN
            expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
            expect(submissionPolicyServiceStub).toHaveBeenCalledOnce();
            expect(getTemplateRepositoryFilesStub).toHaveBeenCalledOnce();
            expect(getSolutionRepositoryFilesStub).toHaveBeenCalledOnce();
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            await Promise.resolve();
            expect(comp.programmingExercise).toEqual(mockProgrammingExercise);
            expect(comp.isExamExercise).toBeFalse();
            expect(comp.doughnutStats.participationsInPercent).toBe(100);
            expect(comp.doughnutStats.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats.absoluteAveragePoints).toBe(5);
            expect(comp.repositoryDiffInformation).toBeDefined();
            expect(comp.repositoryDiffInformation!.diffInformations).toHaveLength(1);
        });

        it.each([true, false])(
            'should only call service method to get build log statistics onInit if the user is at least an editor for this exercise',
            async (isEditor: boolean) => {
                const programmingExercise = new ProgrammingExercise(new Course(), undefined);
                programmingExercise.id = 123;
                programmingExercise.isAtLeastEditor = isEditor;
                jest.spyOn(exerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
                    of({ body: programmingExercise } as unknown as HttpResponse<ProgrammingExercise>),
                );
                comp.ngOnInit();
                await new Promise((r) => setTimeout(r, 100));
            },
        );

        it('should handle repositoryFilesError gracefully', fakeAsync(() => {
            // Create a fresh programming exercise for this test
            const testExercise = new ProgrammingExercise(new Course(), undefined);
            testExercise.id = 456; // Different ID to avoid conflicts

            // Set up the route data for this test
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ programmingExercise: testExercise });

            const errorSpy = jest.spyOn(alertService, 'error');

            // Mock the repository file services to throw errors only for this test
            const templateFilesSpy = jest
                .spyOn(exerciseService, 'getTemplateRepositoryTestFilesWithContent')
                .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const solutionFilesSpy = jest
                .spyOn(exerciseService, 'getSolutionRepositoryTestFilesWithContent')
                .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            comp.ngOnInit();
            tick(1000); // Wait for all async operations to complete

            // The error should be reported
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.repositoryFilesError');

            // Repository diff information should not be set due to the error
            expect(comp.repositoryDiffInformation).toBeUndefined();

            // Clean up the spies
            templateFilesSpy.mockRestore();
            solutionFilesSpy.mockRestore();
        }));
    });

    describe('onInit for exam exercise', () => {
        const exam = { id: 4, course: { id: 6 } as Course } as Exam;
        const exerciseGroup = { id: 9, exam };
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.id = 123;
        programmingExercise.exerciseGroup = exerciseGroup;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ programmingExercise });
        });

        it('should be in exam mode', fakeAsync(async () => {
            // WHEN
            comp.ngOnInit();

            tick();

            // THEN
            await Promise.resolve();
            expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            expect(getTemplateRepositoryFilesStub).toHaveBeenCalledOnce();
            expect(getSolutionRepositoryFilesStub).toHaveBeenCalledOnce();
            await Promise.resolve();
            expect(comp.programmingExercise).toEqual(mockProgrammingExercise);
            expect(comp.isExamExercise).toBeTrue();
            expect(comp.repositoryDiffInformation).toBeDefined();
            expect(comp.repositoryDiffInformation!.diffInformations).toHaveLength(1);
        }));
    });

    it('should create details', () => {
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.id = 123;
        comp.programmingExercise = programmingExercise;

        const sections = comp.getExerciseDetails();
        expect(sections).toBeDefined();
    });

    it.each([['jenkins', true]])('should show the build plan edit button for profile %s: %s', (profile, editable) => {
        profileInfo.activeProfiles = [profile];
        const profileInfoStub = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        comp.ngOnInit();

        expect(profileInfoStub).toHaveBeenCalled();
        expect(comp.isBuildPlanEditable).toBe(editable);
    });

    it('should delete programming exercise', () => {
        const routerNavigateSpy = jest.spyOn(router, 'navigateByUrl');
        jest.spyOn(exerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: null })));
        comp.programmingExercise = mockProgrammingExercise;
        comp.deleteProgrammingExercise({});
        expect(routerNavigateSpy).toHaveBeenCalledOnce();
    });

    it('should delete exam programming exercise', () => {
        const routerNavigateSpy = jest.spyOn(router, 'navigateByUrl');
        jest.spyOn(exerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: null })));
        comp.programmingExercise = mockProgrammingExercise;
        comp.isExamExercise = true;
        comp.deleteProgrammingExercise({});
        expect(routerNavigateSpy).toHaveBeenCalledOnce();
    });

    it('should generate structure oracle', async () => {
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        jest.spyOn(exerciseService, 'generateStructureOracle').mockReturnValue(of('success'));
        comp.programmingExercise = mockProgrammingExercise;
        comp.generateStructureOracle();
        expect(alertSpy).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'success',
            disableTranslation: true,
        });
    });

    it('should error on generate structure oracle', () => {
        const alertSpy = jest.spyOn(alertService, 'addAlert');
        jest.spyOn(exerciseService, 'generateStructureOracle').mockReturnValue(
            throwError(
                () =>
                    new HttpErrorResponse({
                        status: 500,
                        error: { message: 'Some error occurred.' },
                        headers: new HttpHeaders({ 'X-artemisApp-alert': 'error' }),
                    }),
            ),
        );
        comp.programmingExercise = mockProgrammingExercise;
        comp.generateStructureOracle();
        expect(alertSpy).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'error',
            disableTranslation: true,
        });
    });
});
