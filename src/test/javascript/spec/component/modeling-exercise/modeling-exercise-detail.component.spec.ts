import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise-detail.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import sinonChai from 'sinon-chai';
import * as chai from 'chai';
import * as sinon from 'sinon';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { EventManager } from 'app/core/util/event-manager.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ModelingExercise Management Detail Component', () => {
    let comp: ModelingExerciseDetailComponent;
    let fixture: ComponentFixture<ModelingExerciseDetailComponent>;
    let modelingExerciseService: ModelingExerciseService;
    let eventManager: EventManager;
    let statisticsService: StatisticsService;

    const model = { element: { id: '33' } };
    const modelingExercise = { id: 123, sampleSolutionModel: JSON.stringify(model) } as ModelingExercise;
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
            imports: [ArtemisTestModule],
            declarations: [ModelingExerciseDetailComponent, MockComponent(NonProgrammingExerciseDetailCommonActionsComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .overrideTemplate(ModelingExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(ModelingExerciseDetailComponent);
        comp = fixture.componentInstance;
        modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);
        statisticsService = fixture.debugElement.injector.get(StatisticsService);
        eventManager = fixture.debugElement.injector.get(EventManager);
        fixture.detectChanges();
    });

    it('Should load exercise on init', fakeAsync(() => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        const findStub = sinon.stub(modelingExerciseService, 'find').returns(
            of(
                new HttpResponse({
                    body: modelingExercise,
                    headers,
                }),
            ),
        );
        const statisticsServiceStub = sinon.stub(statisticsService, 'getExerciseStatistics').returns(of(modelingExerciseStatistics));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(findStub).to.have.been.called;
        expect(statisticsServiceStub).to.have.been.called;
        expect(comp.modelingExercise).to.deep.equal(modelingExercise);
        expect(comp.doughnutStats.participationsInPercent).to.equal(100);
        expect(comp.doughnutStats.resolvedPostsInPercent).to.equal(50);
        expect(comp.doughnutStats.absoluteAveragePoints).to.equal(5);
        expect(eventManager.subscribe).to.have.been.calledWith('modelingExerciseListModification');
        tick();
        expect(comp.sampleSolutionUML).to.deep.equal(model);
    }));

    it('should destroy event manager on destroy', () => {
        comp.ngOnDestroy();
        expect(eventManager.destroy).to.have.been.called;
    });
});
