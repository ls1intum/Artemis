import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock the diff.utils module to avoid Monaco Editor issues in tests — must be hoisted above imports.
vi.mock('app/programming/shared/utils/diff.utils', async () => ({
    ...(await vi.importActual<typeof import('app/programming/shared/utils/diff.utils')>('app/programming/shared/utils/diff.utils')),
    processRepositoryDiff: vi.fn().mockImplementation((templateFiles, solutionFiles) => {
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

import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of } from 'rxjs';
import { ProgrammingExerciseDetailComponent } from 'app/programming/manage/detail/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateModule } from '@ngx-translate/core';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { MockProgrammingExerciseGradingService } from 'test/helpers/mocks/service/mock-programming-exercise-grading.service';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DetailOverviewListComponent } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';

/*
 *  separate test spec file for sharing aspects of the programming details component. Could be merged into programming-exercise-detail.component.spec.ts on the long run.
 */
describe('ProgrammingExerciseDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseDetailComponent;
    let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
    let statisticsService: StatisticsService;
    let exerciseService: ProgrammingExerciseService;
    let submissionPolicyService: SubmissionPolicyService;
    let programmingLanguageFeatureService: ProgrammingLanguageFeatureService;

    const mockProgrammingExercise = Object.assign(new ProgrammingExercise(new Course(), undefined), {
        id: 1,
        categories: [{ category: 'Important' }],
        templateParticipation: { id: 1 } as TemplateProgrammingExerciseParticipation,
        solutionParticipation: { id: 2 } as SolutionProgrammingExerciseParticipation,
    });

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
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: Router, useClass: MockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // Mock the heavy presentational children so the eager zoneless render does not pull in
            // their own dependencies (e.g. DialogService) or crash on missing inputs (doughnut chart).
            .overrideComponent(ProgrammingExerciseDetailComponent, {
                remove: {
                    imports: [ExerciseDetailStatisticsComponent, DetailOverviewListComponent, DocumentationButtonComponent],
                },
                add: {
                    imports: [MockComponent(ExerciseDetailStatisticsComponent), MockComponent(DetailOverviewListComponent), MockComponent(DocumentationButtonComponent)],
                },
            })
            .compileComponents();

        // Mock the ResizeObserver, which is not available in the test environment
        const originalResizeObserver = global.ResizeObserver;
        (global as any).__origResizeObserver = originalResizeObserver;
        global.ResizeObserver = vi.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(ProgrammingExerciseDetailComponent);
        comp = fixture.componentInstance;

        statisticsService = TestBed.inject(StatisticsService);
        vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));

        exerciseService = TestBed.inject(ProgrammingExerciseService);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);

        programmingLanguageFeatureService = TestBed.inject(ProgrammingLanguageFeatureService);

        vi.spyOn(exerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: mockProgrammingExercise })),
        );
        vi.spyOn(exerciseService, 'getTemplateRepositoryTestFilesWithContent').mockReturnValue(
            of(new Map([[mockDiffInformation.diffInformations[0].originalPath, mockDiffInformation.diffInformations[0].originalFileContent ?? '']])),
        );
        vi.spyOn(exerciseService, 'getSolutionRepositoryTestFilesWithContent').mockReturnValue(
            of(new Map([[mockDiffInformation.diffInformations[0].modifiedPath, mockDiffInformation.diffInformations[0].modifiedFileContent ?? '']])),
        );
        vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));

        vi.spyOn(programmingLanguageFeatureService, 'getProgrammingLanguageFeature').mockReturnValue({
            plagiarismCheckSupported: true,
        } as ProgrammingLanguageFeature);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        // Restore original ResizeObserver to avoid cross-test pollution
        const orig = (global as any).__origResizeObserver;
        if (orig) {
            global.ResizeObserver = orig;
            delete (global as any).__origResizeObserver;
        }
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

        it('should be in sharing mode', () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            // THEN
            expect(comp.isExportToSharingEnabled).toBeFalsy();
        });

        it('should not be in sharing mode', () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;

            // THEN
            expect(comp.isExportToSharingEnabled).toBeFalsy();
        });

        it('should not be in sharing mode because profile enabled but body empty', () => {
            // WHEN
            comp.ngOnInit();
            comp.programmingExercise = mockProgrammingExercise;
            comp.programmingExerciseBuildConfig = mockProgrammingExercise.buildConfig;

            // THEN
            expect(comp.isExportToSharingEnabled).toBeFalsy();
        });
    });
});
