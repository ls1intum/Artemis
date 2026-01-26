import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { ChartNode, ExerciseScoresChartComponent } from 'app/core/course/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { of } from 'rxjs';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/core/course/overview/visualizations/exercise-scores-chart.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

class MockActivatedRoute {
    parent: any;
    params: any;

    constructor(options: { parent?: any; params?: any }) {
        this.parent = options.parent;
        this.params = options.params;
    }
}

const mockActivatedRoute = new MockActivatedRoute({
    parent: {
        parent: new MockActivatedRoute({
            params: of({ courseId: '1' }),
        }),
    },
});

describe('ExerciseScoresChartComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseScoresChartComponent>;
    let component: ExerciseScoresChartComponent;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                MockProvider(AlertService),
                MockProvider(ArtemisNavigationUtilService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ExerciseScoresChartService),

                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
                provideNoopAnimationsForTests(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(ExerciseScoresChartComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const exerciseScoresChartService = TestBed.inject(ExerciseScoresChartService);
        const exerciseScoresResponse: HttpResponse<ExerciseScoresDTO[]> = new HttpResponse({
            body: [],
            status: 200,
        });
        vi.spyOn(exerciseScoresChartService, 'getExerciseScoresForCourse').mockReturnValue(of(exerciseScoresResponse));
        fixture.componentRef.setInput('filteredExerciseIDs', []);
        fixture.detectChanges();
        expect(component.courseId).toBe(1);
    });

    it('should load exercise scores and generate chart', () => {
        const firstExercise = generateExerciseScoresDTO(ExerciseType.TEXT, 1, 50, 70, 100, dayjs(), 'First Exercise');
        const secondExercise = generateExerciseScoresDTO(ExerciseType.QUIZ, 2, 40, 80, 90, dayjs().add(5, 'days'), 'Second Exercise');

        const getScoresStub = setUpServiceAndStartComponent([firstExercise, secondExercise]);
        expect(getScoresStub).toHaveBeenCalledOnce();
        expect(component.ngxData).toHaveLength(3);

        // datasets[0] is student score
        expect(component.ngxData[0].series).toHaveLength(2);
        const studentFirstExerciseDataPoint = component.ngxData[0].series[0];
        const sutdentSecondExerciseDataPoint = component.ngxData[0].series[1];
        validateStructureOfDataPoint(studentFirstExerciseDataPoint, firstExercise, firstExercise.scoreOfStudent!);
        validateStructureOfDataPoint(sutdentSecondExerciseDataPoint, secondExercise, secondExercise.scoreOfStudent!);

        // datasets[1] is average score
        expect(component.ngxData[1].series).toHaveLength(2);
        const averageFirstExerciseDataPoint = component.ngxData[1].series[0];
        const averageSecondExerciseDataPoint = component.ngxData[1].series[1];
        validateStructureOfDataPoint(averageFirstExerciseDataPoint, firstExercise, firstExercise.averageScoreAchieved!);
        validateStructureOfDataPoint(averageSecondExerciseDataPoint, secondExercise, secondExercise.averageScoreAchieved!);

        // datasets[2] is best score
        expect(component.ngxData[2].series).toHaveLength(2);
        const bestFirstExerciseDataPoint = component.ngxData[2].series[0];
        const bestSecondExerciseDataPoint = component.ngxData[2].series[1];
        validateStructureOfDataPoint(bestFirstExerciseDataPoint, firstExercise, firstExercise.maxScoreAchieved!);
        validateStructureOfDataPoint(bestSecondExerciseDataPoint, secondExercise, secondExercise.maxScoreAchieved!);
    });

    it('should filter exercises correctly', () => {
        const exercises = [];
        for (let i = 0; i < 10; i++) {
            exercises.push(generateExerciseScoresDTO(ExerciseType.QUIZ, i, i * 5, 100 - i * 4, 100 - i * 4, dayjs().add(i, 'days'), i + 'th Exercise'));
        }

        // Set up the component with filtered exercise IDs already set
        const exerciseScoresChartService = TestBed.inject(ExerciseScoresChartService);
        const exerciseScoresResponse: HttpResponse<ExerciseScoresDTO[]> = new HttpResponse({
            body: exercises,
            status: 200,
        });

        // Set initial filter with some exercises filtered out
        fixture.componentRef.setInput('filteredExerciseIDs', [2, 4, 5]);
        const getScoresStub = vi.spyOn(exerciseScoresChartService, 'getExerciseScoresForCourse').mockReturnValue(of(exerciseScoresResponse));
        component.ngAfterViewInit();

        expect(getScoresStub).toHaveBeenCalledOnce();
        // Should only contain the not filtered exercises (filtered out: 2, 4, 5)
        expect(component.ngxData[0].series.map((exercise: any) => exercise.exerciseId)).toEqual([0, 1, 3, 6, 7, 8, 9]);

        // Now test changing the filter - this should use the cached data
        // Reset excludedExerciseScores to ensure proper restoration
        component.excludedExerciseScores = exercises.filter((e) => [2, 4, 5].includes(e.exerciseId!));
        component.exerciseScores = exercises.filter((e) => ![2, 4, 5].includes(e.exerciseId!));

        // Manually trigger the initialization logic with new filter
        // by directly calling the method that would be called by the effect
        fixture.componentRef.setInput('filteredExerciseIDs', []);

        // Instead of flushEffects, manually call initializeChart to test the filtering logic
        // This tests the component's filtering behavior without lifecycle interference
        (component as any).initializeChart();

        expect(component.ngxData[0].series.map((exercise: any) => exercise.exerciseId)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);

        // The service should not be called again when just filtering
        expect(getScoresStub).toHaveBeenCalledOnce();
    });

    it('should react correctly if legend entry is clicked', () => {
        const firstExercise = generateExerciseScoresDTO(ExerciseType.FILE_UPLOAD, 1, 40, 30, 60, dayjs(), 'first exercise');
        const secondExercise = generateExerciseScoresDTO(ExerciseType.FILE_UPLOAD, 2, 43, 31, 70, dayjs(), 'second exercise');

        setUpServiceAndStartComponent([firstExercise, secondExercise]);
        const legendClickEvent = 'artemisApp.exercise-scores-chart.maximumScoreLabel';
        component.onSelect(legendClickEvent);

        expect(component.ngxColor.domain[2]).toBe('rgba(255,255,255,0)');
        expect(component.ngxData[2].series).toEqual([]);

        component.onSelect(legendClickEvent);
        expect(component.ngxColor.domain[2]).toBe(GraphColors.GREEN);
        expect(component.ngxData[2].series.map((exercise: any) => exercise.value)).toEqual([60, 70]);
    });

    it('should react correct if chart point is clicked', () => {
        const firstExercise = generateExerciseScoresDTO(ExerciseType.TEXT, 1, 40, 30, 60, dayjs(), 'first exercise');
        const secondExercise = generateExerciseScoresDTO(ExerciseType.QUIZ, 2, 43, 31, 70, dayjs(), 'second exercise');

        setUpServiceAndStartComponent([firstExercise, secondExercise]);
        const routingService = TestBed.inject(ArtemisNavigationUtilService);
        const routingStub = vi.spyOn(routingService, 'routeInNewTab');
        const pointClickEvent: ChartNode = { exerciseType: '', name: '', series: '', value: 0, exerciseId: 2 };

        component.onSelect(pointClickEvent);

        expect(routingStub).toHaveBeenCalledWith(['courses', 1, 'exercises', 2]);

        pointClickEvent.exerciseId = 1;

        component.onSelect(pointClickEvent);

        expect(routingStub).toHaveBeenCalledWith(['courses', 1, 'exercises', 1]);
    });

    const setUpServiceAndStartComponent = (exerciseDTOs: ExerciseScoresDTO[]) => {
        const exerciseScoresChartService = TestBed.inject(ExerciseScoresChartService);
        const exerciseScoresResponse: HttpResponse<ExerciseScoresDTO[]> = new HttpResponse({
            body: exerciseDTOs,
            status: 200,
        });
        fixture.componentRef.setInput('filteredExerciseIDs', []);
        const getScoresStub = vi.spyOn(exerciseScoresChartService, 'getExerciseScoresForCourse').mockReturnValue(of(exerciseScoresResponse));
        component.ngAfterViewInit();
        return getScoresStub;
    };
});

function validateStructureOfDataPoint(dataPoint: any, exerciseScoresDTO: ExerciseScoresDTO, score: number) {
    const expectedStructure = { name: exerciseScoresDTO.exerciseTitle, value: score, exerciseId: exerciseScoresDTO.exerciseId, exerciseType: exerciseScoresDTO.exerciseType };
    expect(dataPoint).toEqual(expectedStructure);
}

function generateExerciseScoresDTO(
    exerciseType: ExerciseType,
    exerciseId: number,
    scoreOfStudent: number,
    averageScore: number,
    maxScore: number,
    releaseDate: dayjs.Dayjs,
    exerciseTitle: string,
) {
    const dto = new ExerciseScoresDTO();
    dto.exerciseType = exerciseType;
    dto.exerciseId = exerciseId;
    dto.scoreOfStudent = scoreOfStudent;
    dto.averageScoreAchieved = averageScore;
    dto.maxScoreAchieved = maxScore;
    dto.releaseDate = releaseDate;
    dto.exerciseTitle = exerciseTitle;
    return dto;
}
