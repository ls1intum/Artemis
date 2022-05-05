import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { RouterTestingModule } from '@angular/router/testing';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PerformanceInterval, StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ExerciseType } from 'app/entities/exercise.model';
import { GraphColors } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

describe('StatisticsAverageScoreGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsAverageScoreGraphComponent>;
    let component: StatisticsAverageScoreGraphComponent;
    let routingStub: jest.SpyInstance;

    const exercise1 = { exerciseId: 1, exerciseName: 'FarcadePattern', averageScore: 0, exerciseType: ExerciseType.TEXT };
    const exercise2 = { exerciseId: 2, exerciseName: 'BridgePattern', averageScore: 20, exerciseType: ExerciseType.PROGRAMMING };
    const exercise3 = { exerciseId: 3, exerciseName: 'VisitorPattern', averageScore: 25, exerciseType: ExerciseType.FILE_UPLOAD };
    const exercise4 = { exerciseId: 4, exerciseName: 'AdapterPattern', averageScore: 35, exerciseType: ExerciseType.QUIZ };
    const exercise5 = { exerciseId: 5, exerciseName: 'ProxyPattern', averageScore: 40, exerciseType: ExerciseType.MODELING };
    const exercise6 = { exerciseId: 6, exerciseName: 'BuilderPattern', averageScore: 50, exerciseType: ExerciseType.QUIZ };
    const exercise7 = { exerciseId: 7, exerciseName: 'BehaviouralPattern', averageScore: 55, exerciseType: ExerciseType.PROGRAMMING };
    const exercise8 = { exerciseId: 8, exerciseName: 'SingletonPattern', averageScore: 56, exerciseType: ExerciseType.TEXT };
    const exercise9 = { exerciseId: 9, exerciseName: 'ObserverPattern', averageScore: 60, exerciseType: ExerciseType.FILE_UPLOAD };
    const exercise10 = { exerciseId: 10, exerciseName: 'StrategyPattern', averageScore: 75, exerciseType: ExerciseType.PROGRAMMING };
    const exercise11 = { exerciseId: 11, exerciseName: 'StatePattern', averageScore: 100, exerciseType: ExerciseType.MODELING };

    const returnValue = [exercise2, exercise4, exercise5, exercise8, exercise9, exercise10, exercise6, exercise11, exercise1, exercise3, exercise7];

    const courseAverageScore = 75;

    const exerciseTypes = [ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.QUIZ, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
    const exerciseTypeStrings = ['text', 'programming', 'file-upload', 'quiz', 'modeling', 'quiz', 'programming', 'text', 'file-upload', 'programming', 'modeling'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [StatisticsAverageScoreGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisNavigationUtilService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsAverageScoreGraphComponent);
                component = fixture.componentInstance;
                const routingService = TestBed.inject(ArtemisNavigationUtilService);
                routingStub = jest.spyOn(routingService, 'routeInNewTab');

                component.exerciseAverageScores = returnValue;
                component.courseAverage = courseAverageScore;
                fixture.detectChanges();
            });
    });

    it('should initialize', () => {
        const averageExerciseScores = [0, 20, 25, 35, 40, 50, 55, 56, 60, 75];
        const expectedTypes = [
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
            expect(component.ngxData[i].exerciseType).toBe(expectedTypes[i]);
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
            expect(routingStub).toHaveBeenCalledWith(path);
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

    describe('test filtering', () => {
        const programmingExercises = [exercise2, exercise7, exercise10];
        const modelingExercises = [exercise5, exercise11];
        const quizExercises = [exercise4, exercise6];
        const textExercises = [exercise1, exercise8];
        const fileUploadExercises = [exercise3, exercise9];
        let expectedScores: CourseManagementStatisticsModel[];

        it.each(exerciseTypes)('should filter for type correctly if only one type is selected', (type: ExerciseType) => {
            toggleAllTypes();

            component.toggleType(type);

            if (type === ExerciseType.PROGRAMMING) {
                expect(component.currentlyDisplayableExercises).toEqual(programmingExercises);
                expect(component.currentSize).toBe(programmingExercises.length);
            } else if (type === ExerciseType.MODELING) {
                expect(component.currentlyDisplayableExercises).toEqual(modelingExercises);
                expect(component.currentSize).toBe(modelingExercises.length);
            } else if (type === ExerciseType.QUIZ) {
                expect(component.currentlyDisplayableExercises).toEqual(quizExercises);
                expect(component.currentSize).toBe(quizExercises.length);
            } else if (type === ExerciseType.TEXT) {
                expect(component.currentlyDisplayableExercises).toEqual(textExercises);
                expect(component.currentSize).toBe(textExercises.length);
            } else {
                expect(component.currentlyDisplayableExercises).toEqual(fileUploadExercises);
                expect(component.currentSize).toBe(fileUploadExercises.length);
            }

            expect(component.currentPeriod).toBe(0);
        });

        it('should filter correctly if lowest third is selected', () => {
            expectedScores = [exercise1, exercise2, exercise3];

            component.togglePerformanceInterval(PerformanceInterval.LOWEST);

            expect(component.currentlyDisplayableExercises).toEqual(expectedScores);
        });

        it('should filter correctly if average third is selected', () => {
            expectedScores = [exercise4, exercise5, exercise6, exercise7, exercise8];

            component.togglePerformanceInterval(PerformanceInterval.AVERAGE);

            expect(component.currentlyDisplayableExercises).toEqual(expectedScores);
        });

        it('should filter correctly if best third is selected', () => {
            expectedScores = [exercise9, exercise10, exercise11];

            component.togglePerformanceInterval(PerformanceInterval.BEST);

            expect(component.currentlyDisplayableExercises).toEqual(expectedScores);
        });

        it('should filter correctly if lowest and average third is selected', () => {
            expectedScores = [exercise1, exercise2, exercise3, exercise4, exercise5, exercise6, exercise7, exercise8];

            component.togglePerformanceInterval(PerformanceInterval.AVERAGE);
            component.togglePerformanceInterval(PerformanceInterval.LOWEST);

            expect(component.currentlyDisplayableExercises).toEqual(expectedScores);
        });

        it('should filter correctly if lowest and best third is selected', () => {
            expectedScores = [exercise1, exercise2, exercise3, exercise9, exercise10, exercise11];

            component.togglePerformanceInterval(PerformanceInterval.BEST);
            component.togglePerformanceInterval(PerformanceInterval.LOWEST);

            expect(component.currentlyDisplayableExercises).toEqual(expectedScores);
        });

        it('should filter correctly if median and best third is selected', () => {
            expectedScores = [exercise4, exercise5, exercise6, exercise7, exercise8, exercise9, exercise10, exercise11];

            component.togglePerformanceInterval(PerformanceInterval.BEST);
            component.togglePerformanceInterval(PerformanceInterval.AVERAGE);

            expect(component.currentlyDisplayableExercises).toEqual(expectedScores);
        });
    });

    const toggleAllTypes = () => {
        exerciseTypes.forEach((type) => {
            component.toggleType(type);
        });
    };
});
