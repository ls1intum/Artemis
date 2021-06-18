import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MomentModule } from 'ngx-moment';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { SpanType } from 'app/entities/statistics.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { of } from 'rxjs';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { SinonStub } from 'sinon';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseDetailStatisticsComponent } from 'app/exercises/shared/statistics/exercise-detail-statistics.component';
import { TranslateService } from '@ngx-translate/core';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseStatisticsComponent', () => {
    let fixture: ComponentFixture<ExerciseStatisticsComponent>;
    let component: ExerciseStatisticsComponent;
    let service: StatisticsService;
    let exerciseService: ExerciseService;

    let statisticsSpy: SinonStub;
    let exerciseSpy: SinonStub;

    const exercise = {
        id: 1,
        course: {
            id: 2,
        },
    } as Exercise;

    const exerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfQuestions: 4,
        numberOfAnsweredQuestions: 2,
        questionsAnsweredInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                ExerciseStatisticsComponent,
                MockComponent(AlertComponent),
                MockComponent(StatisticsGraphComponent),
                MockComponent(StatisticsAverageScoreGraphComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(StatisticsScoreDistributionGraphComponent),
                MockComponent(ExerciseDetailStatisticsComponent),
            ],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseStatisticsComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(StatisticsService);
                exerciseService = TestBed.inject(ExerciseService);
                statisticsSpy = sinon.stub(service, 'getExerciseStatistics').returns(of(exerciseStatistics));
                exerciseSpy = sinon.stub(exerciseService, 'find').returns(of({ body: exercise } as HttpResponse<Exercise>));
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(statisticsSpy).to.have.been.calledOnce;
        expect(exerciseSpy).to.have.been.calledOnce;
        expect(component.exerciseStatistics.participationsInPercent).to.equal(100);
        expect(component.exerciseStatistics.questionsAnsweredInPercent).to.equal(50);
        expect(component.exerciseStatistics.absoluteAveragePoints).to.equal(5);
    });

    it('should trigger when tab changed', fakeAsync(() => {
        const tabSpy = sinon.spy(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        tick();
        expect(tabSpy).to.have.been.calledOnce;
        expect(component.currentSpan).to.be.equal(SpanType.MONTH);
        expect(statisticsSpy).to.have.been.calledOnce;
        expect(exerciseSpy).to.have.been.calledOnce;
        expect(component.exerciseStatistics.participationsInPercent).to.equal(100);
        expect(component.exerciseStatistics.questionsAnsweredInPercent).to.equal(50);
        expect(component.exerciseStatistics.absoluteAveragePoints).to.equal(5);
    }));
});
