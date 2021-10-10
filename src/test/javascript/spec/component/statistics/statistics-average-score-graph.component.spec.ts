import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { RouterTestingModule } from '@angular/router/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ChartsModule } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';

chai.use(sinonChai);
const expect = chai.expect;

describe('StatisticsAverageScoreGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsAverageScoreGraphComponent>;
    let component: StatisticsAverageScoreGraphComponent;

    const returnValue = [
        { exerciseId: 1, exerciseName: 'BridgePattern', averageScore: 20 },
        { exerciseId: 2, exerciseName: 'AdapterPattern', averageScore: 35 },
        { exerciseId: 3, exerciseName: 'ProxyPattern', averageScore: 40 },
        { exerciseId: 4, exerciseName: 'SingletonPattern', averageScore: 55 },
        { exerciseId: 5, exerciseName: 'ObserverPattern', averageScore: 60 },
        { exerciseId: 6, exerciseName: 'StrategyPattern', averageScore: 75 },
        { exerciseId: 7, exerciseName: 'BuilderPattern', averageScore: 50 },
        { exerciseId: 8, exerciseName: 'StatePattern', averageScore: 100 },
        { exerciseId: 9, exerciseName: 'FarcadePattern', averageScore: 0 },
        { exerciseId: 10, exerciseName: 'VisitorPattern', averageScore: 25 },
        { exerciseId: 11, exerciseName: 'BehaviouralPattern', averageScore: 55 },
    ];

    const courseAverageScore = 75;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), ChartsModule],
            declarations: [StatisticsAverageScoreGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsAverageScoreGraphComponent);
                component = fixture.componentInstance;

                component.exerciseAverageScores = returnValue;
                component.courseAverage = courseAverageScore;
                fixture.detectChanges();
            });
    });

    it('should initialize', () => {
        const averageScoreCourse = new Array(10).fill(courseAverageScore);
        const averageExerciseScores: number[] = [];
        for (let i = 0; i < 10; i++) {
            averageExerciseScores.push(returnValue[i].averageScore);
        }

        // take first 10 exerciseTitles
        expect(component.barChartLabels).to.deep.equal([
            'BridgePattern',
            'AdapterPattern',
            'ProxyPattern',
            'SingletonPattern',
            'ObserverPattern',
            'StrategyPattern',
            'BuilderPattern',
            'StatePattern',
            'FarcadePattern',
            'VisitorPattern',
        ]);
        expect(component.chartData[0]['data']).to.deep.equal(averageScoreCourse);
        expect(component.chartData[1]['data']).to.deep.equal(averageExerciseScores);
    });

    it('should switch time span', () => {
        component.switchTimeSpan(true);

        // remove first one and push one to the end
        expect(component.barChartLabels).to.deep.equal([
            'AdapterPattern',
            'ProxyPattern',
            'SingletonPattern',
            'ObserverPattern',
            'StrategyPattern',
            'BuilderPattern',
            'StatePattern',
            'FarcadePattern',
            'VisitorPattern',
            'BehaviouralPattern',
        ]);
    });
});
