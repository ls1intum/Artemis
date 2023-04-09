import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TestCaseDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-distribution-chart.component';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TestCaseStatsMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

describe('Test case distribution chart', () => {
    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    let component: TestCaseDistributionChartComponent;
    let fixture: ComponentFixture<TestCaseDistributionChartComponent>;

    let instantSpy: jest.SpyInstance;
    let routingStub: jest.SpyInstance;

    const configureComponent = (testCases: ProgrammingExerciseTestCase[]) => {
        configureProgrammingExercise();
        component.testCases = testCases;
    };
    const configureProgrammingExercise = () => {
        programmingExercise.maxPoints = 10;
        programmingExercise.bonusPoints = 0;
        component.exercise = programmingExercise;
    };

    const testCase = {
        id: 1,
        visibility: Visibility.Always,
        weight: 1,
        exercise: programmingExercise,
        bonusPoints: 0,
        bonusMultiplier: 1,
        testName: 'test case',
    } as ProgrammingExerciseTestCase;

    const testCase2 = {
        id: 2,
        visibility: Visibility.Always,
        weight: 1.5,
        exercise: programmingExercise,
        bonusPoints: 0,
        bonusMultiplier: 1,
        testName: 'test case 2',
    } as ProgrammingExerciseTestCase;

    const invisileTestCase = {
        id: 3,
        visibility: Visibility.Never,
        weight: 4,
        exercise: programmingExercise,
        bonusPoints: 1,
        bonusMultiplier: 1,
        testName: 'invisible test case',
    } as ProgrammingExerciseTestCase;

    const testCase4 = {
        id: 4,
        visibility: Visibility.AfterDueDate,
        weight: 2,
        exercise: programmingExercise,
        bonusPoints: 2,
        bonusMultiplier: 1,
        testName: 'test case 4',
    } as ProgrammingExerciseTestCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [TestCaseDistributionChartComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisNavigationUtilService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TestCaseDistributionChartComponent);
        component = fixture.componentInstance;

        const routingService = TestBed.inject(ArtemisNavigationUtilService);
        routingStub = jest.spyOn(routingService, 'routeInNewTab');

        const translationService = TestBed.inject(TranslateService);
        instantSpy = jest.spyOn(translationService, 'instant');
    });

    it('should handle no test cases appropriately', () => {
        configureProgrammingExercise();
        component.ngOnChanges();

        expect(component.testCases).toEqual([]);

        expect(component.ngxWeightData[0].series).toHaveLength(0);
        expect(component.ngxWeightData[1].series).toHaveLength(0);
        expect(component.ngxPointsData[0].series).toHaveLength(0);
    });

    it('should process the test cases correctly', () => {
        configureComponent([testCase]);

        component.ngOnChanges();

        expect(component.ngxWeightData[0].series[0].name).toBe('test case');
        expect(component.ngxWeightData[0].series[0].value).toBe(100);
        expect(component.ngxWeightData[1].series[0].name).toBe('test case');
        expect(component.ngxWeightData[1].series[0].value).toBe(100);

        component.testCases.push(testCase2);

        component.ngOnChanges();

        expect(component.ngxWeightData).toHaveLength(2);
        expect(component.ngxWeightData[0].series).toHaveLength(2);
        expect(component.ngxWeightData[1].series).toHaveLength(2);

        expect(component.ngxPointsData).toHaveLength(1);
        expect(component.ngxPointsData[0].series).toHaveLength(2);
        expect(component.ngxWeightData[1].series[1].name).toBe('test case 2');
        expect(component.ngxWeightData[1].series[1].value).toBe(60);

        testCase.weight = 1.5;
        component.ngOnChanges();

        expect(component.ngxWeightData[0].series[0].value).toBe(50);
        expect(component.ngxWeightData[0].series[1].value).toBe(50);
    });

    it('should compute the relative score correctly', () => {
        const testMap = {
            'test case': { numPassed: 0, numFailed: 0 },
            'test case 4': { numPassed: 3, numFailed: 0 },
        } as TestCaseStatsMap;
        testCase.weight = 1;
        configureComponent([testCase, testCase4]);
        programmingExercise.bonusPoints = 2;
        component.testCaseStatsMap = testMap;
        component.totalParticipations = 1;

        component.ngOnChanges();

        expect(component.testCaseStatsMap['test case 4'].numPassed).toBe(3);
        expect(component.ngxPointsData[0].series[0].name).toBe('test case');
        expect(component.ngxPointsData[0].series[0].value).toBe(0);
        expect(component.ngxPointsData[0].series[1].name).toBe('test case 4');
        expect(component.ngxPointsData[0].series[1].value).toBe(260);
    });

    it('should exclude test cases that are never visible', () => {
        configureComponent([invisileTestCase]);
        programmingExercise.bonusPoints = 2;

        component.ngOnChanges();

        expect(component.ngxWeightData[0].series).toHaveLength(0);
        expect(component.ngxWeightData[1].series).toHaveLength(0);
        expect(component.ngxPointsData[0].series).toHaveLength(0);
    });

    it('should handle negative weights', () => {
        const negativeTestCase = {
            id: 5,
            visibility: Visibility.Always,
            weight: -10,
            exercise: programmingExercise,
            bonusPoints: 0,
            bonusMultiplier: 1,
            testName: 'negative test case',
        } as ProgrammingExerciseTestCase;
        configureComponent([negativeTestCase]);

        component.ngOnChanges();

        expect(component.ngxWeightData[0].series[0].value).toBe(0);
        expect(component.ngxWeightData[1].series[0].value).toBe(0);
        expect(component.ngxPointsData[0].series[0].value).toBe(0);
    });

    it('should delegate the user correctly if clicked on points chart', () => {
        programmingExercise.id = 4;
        programmingExercise.course = { id: 42 };
        component.exercise = programmingExercise;
        const expectedUrl = ['course-management', 42, 'programming-exercises', 4, 'exercise-statistics'];

        component.onSelectPoints();

        expect(routingStub).toHaveBeenCalledWith(expectedUrl);
    });

    it('should emit the correct test case id if clicked on weight and bonus chart', () => {
        const event = { id: 5 };
        const emitStub = jest.spyOn(component.testCaseRowFilter, 'emit').mockImplementation();

        component.onSelectWeight(event);

        expect(emitStub).toHaveBeenCalledWith(5);
        expect(component.tableFiltered).toBeTrue();
    });

    it('should reset table correctly', () => {
        component.tableFiltered = true;
        const emitStub = jest.spyOn(component.testCaseRowFilter, 'emit').mockImplementation();

        component.resetTableFilter();

        expect(emitStub).toHaveBeenCalledWith(-5);
        expect(component.tableFiltered).toBeFalse();
    });

    it('should update the translation', () => {
        const prefix = 'artemisApp.programmingExercise.configureGrading.charts.';
        const labels = ['testCaseWeights.weight', 'testCaseWeights.weightAndBonus', 'testCasePoints.points'];
        component.ngxWeightData = [
            { name: '', series: [] },
            { name: '', series: [] },
        ];
        component.ngxPointsData = [{ name: '', series: [] }];

        component.updateTranslation();

        expect(instantSpy).toHaveBeenCalledTimes(3);
        instantSpy.mock.calls.forEach((calls, index) => {
            expect(calls[0]).toBe(prefix.concat(labels[index]));
        });
        expect(component.ngxWeightData[0].name).toBe(prefix.concat(labels[0]));
        expect(component.ngxWeightData[1].name).toBe(prefix.concat(labels[1]));
        expect(component.ngxPointsData[0].name).toBe(prefix.concat(labels[2]));
    });
});
