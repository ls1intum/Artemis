import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { RouterTestingModule } from '@angular/router/testing';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ExerciseType } from 'app/entities/exercise.model';
import { GraphColors } from 'app/entities/statistics.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';

describe('StatisticsAverageScoreGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsAverageScoreGraphComponent>;
    let component: StatisticsAverageScoreGraphComponent;
    let router: Router;

    const returnValue = [
        { exerciseId: 2, exerciseName: 'BridgePattern', averageScore: 20, exerciseType: ExerciseType.PROGRAMMING },
        { exerciseId: 4, exerciseName: 'AdapterPattern', averageScore: 35, exerciseType: ExerciseType.QUIZ },
        { exerciseId: 5, exerciseName: 'ProxyPattern', averageScore: 40, exerciseType: ExerciseType.MODELING },
        { exerciseId: 8, exerciseName: 'SingletonPattern', averageScore: 56, exerciseType: ExerciseType.TEXT },
        { exerciseId: 9, exerciseName: 'ObserverPattern', averageScore: 60, exerciseType: ExerciseType.FILE_UPLOAD },
        { exerciseId: 10, exerciseName: 'StrategyPattern', averageScore: 75, exerciseType: ExerciseType.PROGRAMMING },
        { exerciseId: 6, exerciseName: 'BuilderPattern', averageScore: 50, exerciseType: ExerciseType.QUIZ },
        { exerciseId: 11, exerciseName: 'StatePattern', averageScore: 100, exerciseType: ExerciseType.MODELING },
        { exerciseId: 1, exerciseName: 'FarcadePattern', averageScore: 0, exerciseType: ExerciseType.TEXT },
        { exerciseId: 3, exerciseName: 'VisitorPattern', averageScore: 25, exerciseType: ExerciseType.FILE_UPLOAD },
        { exerciseId: 7, exerciseName: 'BehaviouralPattern', averageScore: 55, exerciseType: ExerciseType.PROGRAMMING },
    ];

    const courseAverageScore = 75;

    const exerciseTypeStrings = ['text', 'programming', 'file-upload', 'quiz', 'modeling', 'quiz', 'programming', 'text', 'file-upload', 'programming', 'modeling'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [StatisticsAverageScoreGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsAverageScoreGraphComponent);
                component = fixture.componentInstance;
                router = fixture.debugElement.injector.get(Router);

                component.exerciseAverageScores = returnValue;
                component.courseAverage = courseAverageScore;
                fixture.detectChanges();
            });
    });

    it('should initialize', () => {
        const averageExerciseScores = [0, 20, 25, 35, 40, 50, 55, 56, 60, 75];
        const exerciseTypes = [
            ExerciseType.TEXT,
            ExerciseType.PROGRAMMING,
            ExerciseType.FILE_UPLOAD,
            ExerciseType.QUIZ,
            ExerciseType.MODELING,
            ExerciseType.QUIZ,
            ExerciseType.PROGRAMMING,
            ExerciseType.TEXT,
            ExerciseType.FILE_UPLOAD,
            ExerciseType.PROGRAMMING,
            ExerciseType.MODELING,
        ];
        const expectedColors = [...new Array(3).fill(GraphColors.RED), ...new Array(5).fill(GraphColors.GREY), ...new Array(3).fill(GraphColors.GREEN)];
        // take first 10 exerciseTitles

        expect(component.barChartLabels).toEqual([
            'FarcadePattern',
            'BridgePattern',
            'VisitorPattern',
            'AdapterPattern',
            'ProxyPattern',
            'BuilderPattern',
            'BehaviouralPattern',
            'SingletonPattern',
            'ObserverPattern',
            'StrategyPattern',
        ]);
        for (let i = 0; i < averageExerciseScores.length; i++) {
            expect(component.ngxData[i].value).toBe(averageExerciseScores[i]);
            expect(component.ngxData[i].exerciseType).toBe(exerciseTypes[i]);
            expect(component.ngxData[i].exerciseId).toBe(i + 1);
            expect(component.ngxColor.domain[i]).toBe(expectedColors[i]);
        }
    });

    it('should return the correct type stringification for the tooltips', () => {
        for (let i = 0; i < 10; i++) {
            expect(component.convertTypeForTooltip(returnValue[i].exerciseName, returnValue[i].averageScore)).toBe(exerciseTypeStrings[i]);
        }
    });

    it('should delegate the user to the correct pages', () => {
        const routerMock = jest.spyOn(router, 'navigate');
        component.courseId = 42;

        let event: any;
        let path: any[];
        for (let i = 0; i < 10; i++) {
            event = { name: returnValue[i].exerciseName, value: returnValue[i].averageScore };
            path = ['course-management', 42, exerciseTypeStrings[i] + '-exercises', returnValue[i].exerciseId, 'exercise-statistics'];
            if (returnValue[i].exerciseType === ExerciseType.QUIZ) {
                path[4] = 'quiz-point-statistic';
            }
            component.onSelect(event);
            expect(routerMock).toHaveBeenCalledWith(path);
        }
    });

    it('should switch time span', () => {
        component.switchTimeSpan(true);

        // remove first one and push one to the end
        expect(component.barChartLabels).toEqual([
            'BridgePattern',
            'VisitorPattern',
            'AdapterPattern',
            'ProxyPattern',
            'BuilderPattern',
            'BehaviouralPattern',
            'SingletonPattern',
            'ObserverPattern',
            'StrategyPattern',
            'StatePattern',
        ]);
    });
});
