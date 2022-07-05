import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/exam-scores/exam-scores-average-scores-graph.component';
import { ArtemisTestModule } from '../../../test.module';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AggregatedExerciseGroupResult, AggregatedExerciseResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GraphColors } from 'app/entities/statistics.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExerciseType } from 'app/entities/exercise.model';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';

describe('ExamScoresAverageScoresGraphComponent', () => {
    let fixture: ComponentFixture<ExamScoresAverageScoresGraphComponent>;
    let component: ExamScoresAverageScoresGraphComponent;
    let navigateToExerciseMock: jest.SpyInstance;

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(BarChartModule)],
            declarations: [ExamScoresAverageScoresGraphComponent, MockPipe(ArtemisTranslatePipe)],
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
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamScoresAverageScoresGraphComponent);
                component = fixture.componentInstance;
                navigateToExerciseMock = jest.spyOn(component, 'navigateToExercise').mockImplementation();

                component.averageScores = returnValue;
                fixture.detectChanges();
            });
    });

    it('should set ngx data objects and bar colors correctly', () => {
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

    const adaptExpectedData = (averagePoints: number, newColor: string, expectedColorDomain: string[], expectedData: NgxChartsSingleSeriesDataEntry[]) => {
        component.averageScores.averagePoints = averagePoints;
        component.averageScores.averagePercentage = averagePoints * 10;
        expectedColorDomain[0] = newColor;
        expectedData[0].value = averagePoints * 10;
        component.ngxColor.domain = [];
        component.ngxData = [];

        component.ngOnInit();

        executeExpectStatements(expectedData, expectedColorDomain);
    };

    const executeExpectStatements = (expectedData: NgxChartsSingleSeriesDataEntry[], expectedColorDomain: string[]) => {
        expect(component.ngxData).toEqual(expectedData);
        expect(component.ngxColor.domain).toEqual(expectedColorDomain);
    };

    describe('test exercise navigation', () => {
        const event = { name: 'test', value: 3 };
        it('should navigate if event is valid', () => {
            component.lookup['test'] = { exerciseId: 42, exerciseType: ExerciseType.QUIZ };

            component.onSelect(event);

            expect(navigateToExerciseMock).toHaveBeenCalledOnce();
            expect(navigateToExerciseMock).toHaveBeenCalledWith(42, ExerciseType.QUIZ);
        });

        it('should not navigate if exercise id is missing', () => {
            component.lookup['test'] = { exerciseType: ExerciseType.QUIZ };

            component.onSelect(event);

            expect(navigateToExerciseMock).not.toHaveBeenCalled();
        });

        it('should not navigate if exercise type is missing', () => {
            component.lookup['test'] = { exerciseId: 42 };

            component.onSelect(event);

            expect(navigateToExerciseMock).toHaveBeenCalledTimes(0);
        });
    });

    it('should look up absolute value', () => {
        const roundAndPerformLocalConversionSpy = jest.spyOn(component, 'roundAndPerformLocalConversion');
        component.course = { accuracyOfScores: 2 };
        component.lookup['test'] = { absoluteValue: 40 };

        const result = component.lookupAbsoluteValue('test');

        expect(result).toBe('40');
        expect(roundAndPerformLocalConversionSpy).toHaveBeenCalledOnce();
        expect(roundAndPerformLocalConversionSpy).toHaveBeenCalledWith(40);
    });
});
