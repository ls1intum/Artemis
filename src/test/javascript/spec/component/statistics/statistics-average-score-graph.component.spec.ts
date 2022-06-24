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
import { ChartExerciseTypeFilter } from 'app/shared/chart/chart-exercise-type-filter';
import { ChartCategoryFilter } from 'app/shared/chart/chart-category-filter';

describe('StatisticsAverageScoreGraphComponent', () => {
    let fixture: ComponentFixture<StatisticsAverageScoreGraphComponent>;
    let component: StatisticsAverageScoreGraphComponent;
    let routingStub: jest.SpyInstance;
    let typeFilter: ChartExerciseTypeFilter;
    let categoryFilter: ChartCategoryFilter;

    let applyTypeFilterMock: jest.SpyInstance;
    let applyCategoryFilterMock: jest.SpyInstance;

    const exercise1 = {
        exerciseId: 1,
        exerciseName: 'FarcadePattern',
        averageScore: 0,
        exerciseType: ExerciseType.TEXT,
        categories: [{ color: '#347aeb', category: 'structural pattern' }],
    };
    const exercise2 = {
        exerciseId: 2,
        exerciseName: 'BridgePattern',
        averageScore: 20,
        exerciseType: ExerciseType.PROGRAMMING,
        categories: [{ color: '#347aeb', category: 'structural pattern' }],
    };
    const exercise3 = {
        exerciseId: 3,
        exerciseName: 'VisitorPattern',
        averageScore: 25,
        exerciseType: ExerciseType.FILE_UPLOAD,
        categories: [{ color: '#c034eb', category: 'behavioral pattern' }],
    };
    const exercise4 = {
        exerciseId: 4,
        exerciseName: 'AdapterPattern',
        averageScore: 35,
        exerciseType: ExerciseType.QUIZ,
        categories: [{ color: '#347aeb', category: 'structural pattern' }],
    };
    const exercise5 = {
        exerciseId: 5,
        exerciseName: 'ProxyPattern',
        averageScore: 40,
        exerciseType: ExerciseType.MODELING,
        categories: [{ color: '#347aeb', category: 'structural pattern' }],
    };
    const exercise6 = { exerciseId: 6, exerciseName: 'BuilderPattern', averageScore: 50, exerciseType: ExerciseType.QUIZ };
    const exercise7 = { exerciseId: 7, exerciseName: 'BehaviouralPattern', averageScore: 55, exerciseType: ExerciseType.PROGRAMMING };
    const exercise8 = { exerciseId: 8, exerciseName: 'SingletonPattern', averageScore: 56, exerciseType: ExerciseType.TEXT };
    const exercise9 = { exerciseId: 9, exerciseName: 'ObserverPattern', averageScore: 60, exerciseType: ExerciseType.FILE_UPLOAD };
    const exercise10 = { exerciseId: 10, exerciseName: 'StrategyPattern', averageScore: 75, exerciseType: ExerciseType.PROGRAMMING };
    const exercise11 = { exerciseId: 11, exerciseName: 'StatePattern', averageScore: 100, exerciseType: ExerciseType.MODELING };

    const returnValue = [exercise2, exercise4, exercise5, exercise8, exercise9, exercise10, exercise6, exercise11, exercise1, exercise3, exercise7];

    const courseAverageScore = 75;

    const exerciseTypes = [ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.QUIZ, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
    const typeSet: Set<ExerciseType> = new Set(exerciseTypes);
    const categories = ['structural pattern', 'behavioral pattern'];
    const categorySet = new Set<string>(categories);
    const exerciseTypeStrings = ['text', 'programming', 'file-upload', 'quiz', 'modeling', 'quiz', 'programming', 'text', 'file-upload', 'programming', 'modeling'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [StatisticsAverageScoreGraphComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisNavigationUtilService), MockProvider(ChartExerciseTypeFilter), MockProvider(ChartCategoryFilter)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsAverageScoreGraphComponent);
                component = fixture.componentInstance;
                const routingService = TestBed.inject(ArtemisNavigationUtilService);
                routingStub = jest.spyOn(routingService, 'routeInNewTab');
                typeFilter = TestBed.inject(ChartExerciseTypeFilter);
                typeFilter.typeSet = typeSet;
                categoryFilter = TestBed.inject(ChartCategoryFilter);
                categoryFilter.exerciseCategories = categorySet;

                applyTypeFilterMock = jest.spyOn(typeFilter, 'applyCurrentFilter').mockReturnValue(returnValue);
                applyCategoryFilterMock = jest.spyOn(categoryFilter, 'applyCurrentFilter').mockReturnValue(returnValue);

                component.exerciseAverageScores = returnValue;
                component.courseAverage = courseAverageScore;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
        let expectedScores: CourseManagementStatisticsModel[];

        it.each(exerciseTypes)('should filter for type correctly if only one type is selected', (type: ExerciseType) => {
            const toggleTypeMock = jest.spyOn(typeFilter, 'toggleExerciseType').mockReturnValue(returnValue);

            component.toggleType(type);

            expect(toggleTypeMock).toHaveBeenCalledOnce();
            expect(toggleTypeMock).toHaveBeenCalledWith(type, returnValue);
            expect(applyCategoryFilterMock).toHaveBeenCalledOnce();
            expect(applyCategoryFilterMock).toHaveBeenCalledWith(returnValue);
            expect(component.currentlyDisplayableExercises).toStrictEqual(returnValue);

            expect(component.currentPeriod).toBe(0);
        });

        it.each(categories)('should filter for category correctly if only one category is selected', (category: string) => {
            const toggleCategoryMock = jest.spyOn(categoryFilter, 'toggleCategory').mockReturnValue(returnValue);

            component.toggleCategory(category);

            expect(toggleCategoryMock).toHaveBeenCalledOnce();
            expect(toggleCategoryMock).toHaveBeenCalledWith(returnValue, category);
            expect(applyTypeFilterMock).toHaveBeenCalledOnce();
            expect(applyTypeFilterMock).toHaveBeenCalledWith(returnValue);

            expect(component.currentlyDisplayableExercises).toStrictEqual(returnValue);
            expect(component.currentPeriod).toBe(0);
        });

        it('should filter all categories', () => {
            const toggleAllCategoriesMock = jest.spyOn(categoryFilter, 'toggleAllCategories').mockReturnValue(returnValue);

            component.toggleAllCategories();

            expect(toggleAllCategoriesMock).toHaveBeenCalledOnce();
            expect(toggleAllCategoriesMock).toHaveBeenCalledWith(returnValue);
            expect(applyTypeFilterMock).toHaveBeenCalledOnce();
            expect(applyTypeFilterMock).toHaveBeenCalledWith(returnValue);

            expect(component.currentlyDisplayableExercises).toStrictEqual(returnValue);
            expect(component.currentPeriod).toBe(0);
        });

        it('should filter exercises with no category', () => {
            const toggleExercisesWithNoCategoryMock = jest.spyOn(categoryFilter, 'toggleExercisesWithNoCategory').mockReturnValue(returnValue);

            component.toggleExercisesWithNoCategory();

            expect(toggleExercisesWithNoCategoryMock).toHaveBeenCalledOnce();
            expect(toggleExercisesWithNoCategoryMock).toHaveBeenCalledWith(returnValue);
            expect(applyTypeFilterMock).toHaveBeenCalledOnce();
            expect(applyTypeFilterMock).toHaveBeenCalledWith(returnValue);

            expect(component.currentlyDisplayableExercises).toStrictEqual(returnValue);
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
});
