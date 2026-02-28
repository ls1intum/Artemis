import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { ModelingExerciseDetailComponent } from 'app/modeling/manage/detail/modeling-exercise-detail.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { CompetencyExerciseLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';

describe('ModelingExercise Management Detail Component', () => {
    setupTestBed({ zoneless: true });

    let comp: ModelingExerciseDetailComponent;
    let fixture: ComponentFixture<ModelingExerciseDetailComponent>;
    let modelingExerciseService: ModelingExerciseService;
    let eventManager: EventManager;
    let statisticsService: StatisticsService;

    const model = { element: { id: '33' } };
    const modelingExercise = { id: 123, exampleSolutionModel: JSON.stringify(model) } as ModelingExercise;
    const route = { params: of({ exerciseId: modelingExercise.id }) } as any as ActivatedRoute;
    const modelingExerciseStatistics = {
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
            imports: [ModelingExerciseDetailComponent, MockComponent(NonProgrammingExerciseDetailCommonActionsComponent)],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: AlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).overrideTemplate(ModelingExerciseDetailComponent, '');

        fixture = TestBed.createComponent(ModelingExerciseDetailComponent);
        comp = fixture.componentInstance;
        modelingExerciseService = TestBed.inject(ModelingExerciseService);
        statisticsService = TestBed.inject(StatisticsService);
        eventManager = TestBed.inject(EventManager);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load exercise on init', async () => {
        // GIVEN
        const subscribeSpy = vi.spyOn(eventManager, 'subscribe');
        const headers = new HttpHeaders().append('link', 'link;link');
        const findStub = vi.spyOn(modelingExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: modelingExercise,
                    headers,
                }),
            ),
        );
        const statisticsServiceStub = vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(modelingExerciseStatistics));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(findStub).toHaveBeenCalledOnce();
        expect(statisticsServiceStub).toHaveBeenCalledOnce();
        expect(comp.modelingExercise).toEqual(modelingExercise);
        expect(comp.doughnutStats.participationsInPercent).toBe(100);
        expect(comp.doughnutStats.resolvedPostsInPercent).toBe(50);
        expect(comp.doughnutStats.absoluteAveragePoints).toBe(5);
        expect(subscribeSpy).toHaveBeenCalledWith('modelingExerciseListModification', expect.anything());
        await fixture.whenStable();
        expect(comp.exampleSolutionUML).toEqual(model);
    });

    it('should destroy event manager on destroy', () => {
        const destroySpy = vi.spyOn(eventManager, 'destroy');
        comp.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    describe('competency links display', () => {
        it('should display competency links when exercise has competencies', async () => {
            const competency1 = { id: 1, title: 'Competency 1' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Competency 2' } as CourseCompetency;
            const exerciseWithCompetencies = {
                ...modelingExercise,
                competencyLinks: [{ competency: competency1 } as CompetencyExerciseLink, { competency: competency2 } as CompetencyExerciseLink],
            } as ModelingExercise;

            const headers = new HttpHeaders().append('link', 'link;link');
            vi.spyOn(modelingExerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: exerciseWithCompetencies,
                        headers,
                    }),
                ),
            );
            vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(modelingExerciseStatistics));

            comp.ngOnInit();
            await fixture.whenStable();

            expect(comp.detailOverviewSections).toBeDefined();
            const problemSection = comp.detailOverviewSections.find((section) => section.headline === 'artemisApp.exercise.sections.problem');
            expect(problemSection).toBeDefined();
            const competencyDetail = problemSection?.details.find((detail) => detail && 'title' in detail && detail.title === 'artemisApp.competency.link.title');
            expect(competencyDetail).toBeDefined();
            expect(competencyDetail).toHaveProperty('type', DetailType.Text);
            expect(competencyDetail).toHaveProperty('data.text', 'Competency 1, Competency 2');
        });

        it('should not display competency links when exercise has no competencies', async () => {
            const headers = new HttpHeaders().append('link', 'link;link');
            vi.spyOn(modelingExerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: modelingExercise,
                        headers,
                    }),
                ),
            );
            vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(modelingExerciseStatistics));

            comp.ngOnInit();
            await fixture.whenStable();

            expect(comp.detailOverviewSections).toBeDefined();
            const problemSection = comp.detailOverviewSections.find((section) => section.headline === 'artemisApp.exercise.sections.problem');
            expect(problemSection).toBeDefined();
            const competencyDetail = problemSection?.details.find((detail) => detail && 'title' in detail && detail.title === 'artemisApp.competency.link.title');
            expect(competencyDetail).toBeUndefined();
        });
    });
});
