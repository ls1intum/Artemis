import * as sinon from 'sinon';
import * as chai from 'chai';
import * as moment from 'moment';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { ExamPointsSummaryComponent } from 'app/exam/participate/summary/points-summary/exam-points-summary.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Result } from 'app/entities/result.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeDTO } from 'app/entities/grade-step.model';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { GradeType } from 'app/entities/grading-scale.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

chai.use(sinonChai);
const expect = chai.expect;

let fixture: ComponentFixture<ExamPointsSummaryComponent>;
let component: ExamPointsSummaryComponent;

const visibleDate = moment().subtract(7, 'hours');
const startDate = moment().subtract(6, 'hours');
const endDate = moment().subtract(5, 'hours');
const publishResultsDate = moment().subtract(3, 'hours');
const reviewStartDate = moment().subtract(2, 'hours');
const reviewEndDate = moment().add(1, 'hours');

const exam = {
    id: 1,
    title: 'Test Exam',
    visibleDate,
    startDate,
    endDate,
    publishResultsDate,
    reviewStartDate,
    reviewEndDate,
} as Exam;

const textResult = { id: 1, score: 200 } as Result;
const notIncludedTextResult = { id: 99, score: 100 } as Result;
const bonusTextResult = { id: 100, score: 100 } as Result;
const quizResult = { id: 2, score: 20 } as Result;
const modelingResult = { id: 3, score: 33.33 } as Result;
const programmingResult = { id: 4 } as Result;

const user = { id: 1, name: 'Test User' } as User;

const textParticipation = { id: 1, student: user, results: [textResult] } as StudentParticipation;
const notIncludedTextParticipation = { id: 99, student: user, results: [notIncludedTextResult] } as StudentParticipation;
const bonusTextParticipation = { id: 100, student: user, results: [bonusTextResult] } as StudentParticipation;
const quizParticipation = { id: 2, student: user, results: [quizResult] } as StudentParticipation;
const modelingParticipation = { id: 3, student: user, results: [modelingResult] } as StudentParticipation;
const programmingParticipation = { id: 4, student: user, results: [programmingResult] } as StudentParticipation;
const programmingParticipationTwo = { id: 5, student: user } as StudentParticipation;

const textExercise = {
    id: 1,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Text Exercise',
    type: ExerciseType.TEXT,
    studentParticipations: [textParticipation],
    maxPoints: 10,
    bonusPoints: 10,
} as TextExercise;
const notIncludedTextExercise = {
    id: 99,
    includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
    type: ExerciseType.TEXT,
    maxPoints: 10,
    studentParticipations: [notIncludedTextParticipation],
} as TextExercise;
const bonusTextExercise = {
    id: 100,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
    type: ExerciseType.TEXT,
    maxPoints: 10,
    studentParticipations: [bonusTextParticipation],
} as TextExercise;
const quizExercise = {
    id: 2,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Quiz Exercise',
    type: ExerciseType.QUIZ,
    studentParticipations: [quizParticipation],
    maxPoints: 10,
} as QuizExercise;
const modelingExercise = {
    id: 3,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Modeling Exercise',
    type: ExerciseType.MODELING,
    studentParticipations: [modelingParticipation],
    maxPoints: 10,
} as ModelingExercise;
const programmingExercise = {
    id: 4,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Programming Exercise',
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    maxPoints: 10,
} as ProgrammingExercise;
const programmingExerciseTwo = {
    id: 5,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Programming Exercise',
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipationTwo],
} as ProgrammingExercise;
const exercises = [textExercise, quizExercise, modelingExercise, programmingExercise, programmingExerciseTwo, notIncludedTextExercise, bonusTextExercise];

const gradeDto = {
    gradeName: 'Name',
    gradeType: GradeType.GRADE,
    isPassingGrade: true,
} as GradeDTO;

describe('ExamPointsSummaryComponent', function () {
    let gradingSystemService: GradingSystemService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), HttpClientTestingModule],
            declarations: [ExamPointsSummaryComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ExerciseService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamPointsSummaryComponent);
                component = fixture.componentInstance;
                component.courseId = 1;
                component.exam = exam;
                component.exercises = exercises;
                component.gradingScaleExists = false;
                gradingSystemService = TestBed.inject(GradingSystemService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should handle error correctly', () => {
        const gradingSystemServiceMatchPercentageErrorStub = sinon.stub(gradingSystemService, 'matchPercentageToGradeStepForExam').returns(throwError({ status: 404 }));

        fixture.detectChanges();

        expect(fixture).to.be.ok;
        expect(gradingSystemServiceMatchPercentageErrorStub).to.have.been.calledOnce;
        expect(component.gradingScaleExists).to.be.false;
    });

    it('should calculate exam grade correctly', () => {
        const gradingSystemServiceMatchPercentageStub = sinon
            .stub(gradingSystemService, 'matchPercentageToGradeStepForExam')
            .returns(of(new HttpResponse<GradeDTO>({ body: gradeDto })));
        const achievedPointsRelative = (component.calculatePointsSum() / component.calculateMaxPointsSum()) * 100;

        fixture.detectChanges();

        expect(fixture).to.be.ok;
        expect(gradingSystemServiceMatchPercentageStub).to.have.been.calledOnceWithExactly(1, 1, achievedPointsRelative);
        expect(component.gradingScaleExists).to.be.true;
        expect(component.isBonus).to.be.false;
        expect(component.grade).to.equal(gradeDto.gradeName);
        expect(component.hasPassed).to.equal(gradeDto.isPassingGrade);
    });

    it('should initialize and calculate scores correctly', function () {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.calculateAchievedPoints(programmingExerciseTwo)).to.equal(0);
        expect(component.calculateAchievedPoints(textExercise)).to.equal(20);
        expect(component.calculateAchievedPoints(notIncludedTextExercise)).to.equal(10);
        expect(component.calculateAchievedPoints(bonusTextExercise)).to.equal(10);
        expect(component.calculateAchievedPoints(quizExercise)).to.equal(2);
        expect(component.calculateAchievedPoints(modelingExercise)).to.equal(3.3);
        expect(component.calculateAchievedPoints(programmingExercise)).to.equal(0);

        expect(component.calculatePointsSum()).to.equal(35.3);
        expect(component.calculateMaxPointsSum()).to.equal(40);
        expect(component.calculateMaxBonusPointsSum()).to.equal(20);
    });

    it('should display 0 if no exercises are present', function () {
        component.exercises = [];
        fixture.detectChanges();
        expect(fixture).to.be.ok;

        expect(component.calculatePointsSum()).to.equal(0);
        expect(component.calculateMaxPointsSum()).to.equal(0);
    });
});
