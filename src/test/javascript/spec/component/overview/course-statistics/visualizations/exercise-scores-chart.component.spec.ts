import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ChartNode, ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { RouterTestingModule } from '@angular/router/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { LineChartModule } from '@swimlane/ngx-charts';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { GraphColors } from 'app/entities/statistics.model';
import { ArtemisTestModule } from '../../../../test.module';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

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
    let fixture: ComponentFixture<ExerciseScoresChartComponent>;
    let component: ExerciseScoresChartComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(LineChartModule), RouterTestingModule.withRoutes([]), MockModule(BrowserAnimationsModule)],
            declarations: [ExerciseScoresChartComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(AlertService),
                MockProvider(ArtemisNavigationUtilService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ExerciseScoresChartService),

                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseScoresChartComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
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

        const getScoresStub = setUpServiceAndStartComponent(exercises);

        expect(getScoresStub).toHaveBeenCalledOnce();
        expect(component.ngxData[0].series.map((exercise: any) => exercise.exerciseId)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);

        component.filteredExerciseIDs = [2, 4, 5];
        component.ngOnChanges();
        // should not have to reload the data from the server
        expect(getScoresStub).toHaveBeenCalledOnce();
        // should only contain the not filtered exercises
        expect(component.ngxData[0].series.map((exercise: any) => exercise.exerciseId)).toEqual([0, 1, 3, 6, 7, 8, 9]);

        component.filteredExerciseIDs = [];
        component.ngOnChanges();
        expect(getScoresStub).toHaveBeenCalledOnce();
        expect(component.ngxData[0].series.map((exercise: any) => exercise.exerciseId)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
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
        const routingStub = jest.spyOn(routingService, 'routeInNewTab');
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
        component.filteredExerciseIDs = [];
        const getScoresStub = jest.spyOn(exerciseScoresChartService, 'getExerciseScoresForCourse').mockReturnValue(of(exerciseScoresResponse));
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
