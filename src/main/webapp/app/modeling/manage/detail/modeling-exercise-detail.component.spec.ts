import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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

describe('ModelingExercise Management Detail Component', () => {
    let comp: ModelingExerciseDetailComponent;
    let fixture: ComponentFixture<ModelingExerciseDetailComponent>;
    let modelingExerciseService: ModelingExerciseService;
    let eventManager: EventManager;
    let statisticsService: StatisticsService;

    const model = {
        id: 'test-diagram-id',
        version: '4.0.0',
        title: 'Test Diagram',
        type: 'ClassDiagram',
        nodes: [],
        edges: [],
        assessments: {},
    };
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
            declarations: [ModelingExerciseDetailComponent, MockComponent(NonProgrammingExerciseDetailCommonActionsComponent)],
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
        })
            .overrideTemplate(ModelingExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(ModelingExerciseDetailComponent);
        comp = fixture.componentInstance;
        modelingExerciseService = TestBed.inject(ModelingExerciseService);
        statisticsService = TestBed.inject(StatisticsService);
        eventManager = TestBed.inject(EventManager);
        fixture.detectChanges();
    });

    it('should load exercise on init', fakeAsync(() => {
        // GIVEN
        const subscribeSpy = jest.spyOn(eventManager, 'subscribe');
        const headers = new HttpHeaders().append('link', 'link;link');
        const findStub = jest.spyOn(modelingExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: modelingExercise,
                    headers,
                }),
            ),
        );
        const statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(modelingExerciseStatistics));

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
        tick();
        expect(comp.exampleSolutionUML).toEqual(model);
    }));

    it('should destroy event manager on destroy', () => {
        const destroySpy = jest.spyOn(eventManager, 'destroy');
        comp.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
    });
});
