import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { RouterTestingModule } from '@angular/router/testing';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { BarChartModule } from '@swimlane/ngx-charts';

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
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [StatisticsAverageScoreGraphComponent, MockPipe(ArtemisTranslatePipe)],
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
        const averageExerciseScores: number[] = [];
        for (let i = 0; i < 10; i++) {
            averageExerciseScores.push(returnValue[i].averageScore);
        }

        // take first 10 exerciseTitles
        expect(component.barChartLabels).toEqual([
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
        for (let i = 0; i < averageExerciseScores.length; i++) {
            expect(component.ngxData[i].value).toBe(averageExerciseScores[i]);
        }
    });

    it('should switch time span', () => {
        component.switchTimeSpan(true);

        // remove first one and push one to the end
        expect(component.barChartLabels).toEqual([
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
