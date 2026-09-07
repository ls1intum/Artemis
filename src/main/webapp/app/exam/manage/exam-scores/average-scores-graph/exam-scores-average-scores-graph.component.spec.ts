import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/manage/exam-scores/average-scores-graph/exam-scores-average-scores-graph.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AggregatedExerciseGroupResult, AggregatedExerciseResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LocaleConversionService } from 'app/foundation/service/locale-conversion.service';
import { RouterModule } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { TumUiChartSelectEvent } from '@tumaet/ui-angular';

describe('ExamScoresAverageScoresGraphComponent', () => {
    let fixture: ComponentFixture<ExamScoresAverageScoresGraphComponent>;
    let component: ExamScoresAverageScoresGraphComponent;
    let navigateToExerciseMock: ReturnType<typeof vi.spyOn>;

    const returnValue = {
        exerciseGroupId: 1,
        title: 'Patterns',
        averagePoints: 5,
        averagePercentage: 50,
        maxPoints: 10,
        exerciseResults: [
            {
                exerciseId: 2,
                title: 'StrategyPattern',
                maxPoints: 10,
                averagePoints: 6,
                averagePercentage: 60,
            } as AggregatedExerciseResult,
            {
                exerciseId: 3,
                title: 'BridgePattern',
                maxPoints: 10,
                averagePoints: 4,
                averagePercentage: 40,
            } as AggregatedExerciseResult,
            {
                exerciseId: 4,
                title: 'ProxyPattern',
                maxPoints: 10,
                averagePoints: 2,
                averagePercentage: 20,
            } as AggregatedExerciseResult,
        ],
    } as AggregatedExerciseGroupResult;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([])],
            providers: [
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(new HttpResponse({ body: { accuracyOfScores: 1 } }));
                    },
                }),
                MockProvider(LocaleConversionService, {
                    toLocaleString: (score: number) => {
                        return score.toString();
                    },
                }),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamScoresAverageScoresGraphComponent);
        component = fixture.componentInstance;
        navigateToExerciseMock = vi.spyOn(component, 'navigateToExercise').mockImplementation(() => {});

        fixture.componentRef.setInput('averageScores', returnValue);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set chart entries and bar colors correctly', () => {
        const expectedData = [
            { name: 'Patterns', value: 50 },
            { name: '2 StrategyPattern', value: 60 },
            { name: '3 BridgePattern', value: 40 },
            { name: '4 ProxyPattern', value: 20 },
        ];
        const expectedColorDomain = [GraphColors.BLUE, GraphColors.DARK_BLUE, GraphColors.YELLOW, GraphColors.RED];

        executeExpectStatements(expectedData, expectedColorDomain);

        adaptExpectedData(3, GraphColors.YELLOW, expectedColorDomain, expectedData);

        adaptExpectedData(2, GraphColors.RED, expectedColorDomain, expectedData);
    });

    const adaptExpectedData = (averagePoints: number, newColor: string, expectedColorDomain: string[], expectedData: ChartSeriesEntry[]) => {
        component.averageScores().averagePoints = averagePoints;
        component.averageScores().averagePercentage = averagePoints * 10;

        expectedColorDomain[0] = newColor;
        expectedData[0].value = averagePoints * 10;

        component.ngOnInit();

        executeExpectStatements(expectedData, expectedColorDomain);
    };

    const executeExpectStatements = (expectedData: ChartSeriesEntry[], expectedColorDomain: string[]) => {
        expect(component.chartEntries()).toEqual(expectedData);
        expect(component.barColors()).toEqual(expectedColorDomain);
    };

    describe('test exercise navigation', () => {
        // index 1 corresponds to the entry '2 StrategyPattern' in the chart data
        const event: TumUiChartSelectEvent = { seriesIndex: 0, index: 1, label: '2 StrategyPattern' };
        it('should navigate if event is valid', () => {
            component.lookup['2 StrategyPattern'] = { exerciseId: 42, exerciseType: ExerciseType.QUIZ };

            component.onSelect(event);

            expect(navigateToExerciseMock).toHaveBeenCalledOnce();
            expect(navigateToExerciseMock).toHaveBeenCalledWith(42, ExerciseType.QUIZ);
        });

        it('should not navigate if exercise id is missing', () => {
            component.lookup['2 StrategyPattern'] = { exerciseType: ExerciseType.QUIZ };

            component.onSelect(event);

            expect(navigateToExerciseMock).not.toHaveBeenCalled();
        });

        it('should not navigate if exercise type is missing', () => {
            component.lookup['2 StrategyPattern'] = { exerciseId: 42 };

            component.onSelect(event);

            expect(navigateToExerciseMock).not.toHaveBeenCalled();
        });

        it('should not navigate if the click did not hit a bar', () => {
            component.onSelect({ seriesIndex: 0, index: 0 });

            expect(navigateToExerciseMock).not.toHaveBeenCalled();
        });
    });

    describe('chart height', () => {
        // A fixed height squeezed multi-exercise groups into unreadable slivers and made the canvas overflow its
        // box, covering the next chart's title — so the height must grow with the number of bars.
        it('should grow with the number of bars', () => {
            // One bar for the exercise group plus one per exercise: 48px of axis/padding + 4 * 34px.
            expect(component.chartEntries()).toHaveLength(4);
            expect(component.chartHeight()).toBe(48 + 4 * 34);
        });

        it('should shrink for a group with a single exercise', () => {
            const singleExercise = deepClone(returnValue);
            singleExercise.exerciseResults = [deepClone(returnValue.exerciseResults[0])];
            fixture.componentRef.setInput('averageScores', singleExercise);
            component.ngOnInit();

            expect(component.chartEntries()).toHaveLength(2);
            expect(component.chartHeight()).toBe(48 + 2 * 34);
        });

        it('should stay readable for a group without exercises', () => {
            const withoutExercises = deepClone(returnValue);
            withoutExercises.exerciseResults = [];
            fixture.componentRef.setInput('averageScores', withoutExercises);
            component.ngOnInit();

            // Only the exercise-group bar remains, so the box must still be tall enough for one bar plus the axis.
            expect(component.chartHeight()).toBe(48 + 34);
        });
    });

    it('should look up absolute value', () => {
        const roundAndPerformLocalConversionSpy = vi.spyOn(component, 'roundAndPerformLocalConversion');
        const updatedCourse = {
            accuracyOfScores: 2,
        };
        fixture.componentRef.setInput('course', updatedCourse);
        component.lookup['test'] = { absoluteValue: 40 };

        const result = component.lookupAbsoluteValue('test');

        expect(result).toBe('40');
        expect(roundAndPerformLocalConversionSpy).toHaveBeenCalledOnce();
        expect(roundAndPerformLocalConversionSpy).toHaveBeenCalledWith(40);
    });
});
