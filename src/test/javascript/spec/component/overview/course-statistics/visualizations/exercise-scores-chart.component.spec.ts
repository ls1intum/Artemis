import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import { AlertService } from 'app/core/util/alert.service';
import { MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import 'chart.js';
import { CustomChartPoint, ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ChartsModule } from 'ng2-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { RouterTestingModule } from '@angular/router/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs';
import { HttpResponse } from '@angular/common/http';

chai.use(sinonChai);
const expect = chai.expect;

class MockActivatedRoute {
    parent: any;
    params: any;

    constructor(options: { parent?: any; params?: any }) {
        this.parent = options.parent;
        this.params = options.params;
    }
}

const mockActivatedRoute = new MockActivatedRoute({
    parent: new MockActivatedRoute({
        params: of({ courseId: '1' }),
    }),
});

describe('ExerciseScoresChartComponent', () => {
    let fixture: ComponentFixture<ExerciseScoresChartComponent>;
    let component: ExerciseScoresChartComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ChartsModule, RouterTestingModule.withRoutes([])],
            declarations: [ExerciseScoresChartComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                MockProvider(TranslateService),
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

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.courseId).to.equal(1);
    });

    it('should load exercise scores and generate chart', () => {
        const firstExercise = generateExerciseScoresDTO(ExerciseType.TEXT, 1, 50, 70, 100, dayjs(), 'First Exercise');
        const secondExercise = generateExerciseScoresDTO(ExerciseType.QUIZ, 1, 40, 80, 90, dayjs().add(5, 'days'), 'Second Exercise');

        const exerciseScoresChartService = TestBed.inject(ExerciseScoresChartService);
        const exerciseScoresResponse: HttpResponse<ExerciseScoresDTO[]> = new HttpResponse({
            body: [firstExercise, secondExercise],
            status: 200,
        });
        const getScoresStub = sinon.stub(exerciseScoresChartService, 'getExerciseScoresForCourse').returns(of(exerciseScoresResponse));
        fixture.detectChanges();
        expect(getScoresStub).to.have.been.called;
        const chart = component.chartInstance;
        expect(chart).to.be.ok;
        expect(chart.data.datasets).to.have.length(3);
        // datasets[0] is student score
        expect(chart.data.datasets![0].data).to.have.length(2);
        const studentFirstExerciseDataPoint = chart.data.datasets![0].data![0] as CustomChartPoint;
        const sutdentSecondExerciseDataPoint = chart.data.datasets![0].data![1] as CustomChartPoint;
        validateStructureOfDataPoint(studentFirstExerciseDataPoint, firstExercise.exerciseId!, ExerciseType.TEXT, firstExercise.exerciseTitle!, firstExercise.scoreOfStudent!);
        validateStructureOfDataPoint(sutdentSecondExerciseDataPoint, secondExercise.exerciseId!, ExerciseType.QUIZ, secondExercise.exerciseTitle!, secondExercise.scoreOfStudent!);

        // datasets[1] is average score
        expect(chart.data.datasets![1].data).to.have.length(2);
        const averageFirstExerciseDataPoint = chart.data.datasets![1].data![0] as CustomChartPoint;
        const averageSecondExerciseDataPoint = chart.data.datasets![1].data![1] as CustomChartPoint;
        validateStructureOfDataPoint(
            averageFirstExerciseDataPoint,
            firstExercise.exerciseId!,
            ExerciseType.TEXT,
            firstExercise.exerciseTitle!,
            firstExercise.averageScoreAchieved!,
        );
        validateStructureOfDataPoint(
            averageSecondExerciseDataPoint,
            secondExercise.exerciseId!,
            ExerciseType.QUIZ,
            secondExercise.exerciseTitle!,
            secondExercise.averageScoreAchieved!,
        );

        // datasets[2] is best score
        expect(chart.data.datasets![2].data).to.have.length(2);
        const bestFirstExerciseDataPoint = chart.data.datasets![2].data![0] as CustomChartPoint;
        const bestSecondExerciseDataPoint = chart.data.datasets![2].data![1] as CustomChartPoint;
        validateStructureOfDataPoint(bestFirstExerciseDataPoint, firstExercise.exerciseId!, ExerciseType.TEXT, firstExercise.exerciseTitle!, firstExercise.maxScoreAchieved!);
        validateStructureOfDataPoint(bestSecondExerciseDataPoint, secondExercise.exerciseId!, ExerciseType.QUIZ, secondExercise.exerciseTitle!, secondExercise.maxScoreAchieved!);
    });
});

function validateStructureOfDataPoint(dataPoint: CustomChartPoint, exerciseId: number, exerciseType: ExerciseType, exerciseTitle: string, score: number) {
    const expectedStructure = new CustomChartPoint();
    expectedStructure.exerciseId = exerciseId;
    expectedStructure.exerciseTitle = exerciseTitle;
    expectedStructure.y = score;
    expectedStructure.exerciseType = exerciseType;
    expect(dataPoint).to.deep.equal(expectedStructure);
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
