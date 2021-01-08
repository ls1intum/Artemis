import * as sinon from 'sinon';
import * as chai from 'chai';
import * as moment from 'moment';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MockModule, MockPipe } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { ExamPointsSummaryComponent } from 'app/exam/participate/summary/points-summary/exam-points-summary.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Result } from 'app/entities/result.model';

chai.use(sinonChai);
const expect = chai.expect;

let fixture: ComponentFixture<ExamPointsSummaryComponent>;
let component: ExamPointsSummaryComponent;

const visibleDate = moment().subtract(6, 'hours');
const startDate = moment().subtract(5, 'hours');
const endDate = moment().subtract(4, 'hours');
const publishResultsDate = moment().subtract(3, 'hours');
const reviewStartDate = moment().subtract(2, 'hours');
const reviewEndDate = moment().add(1, 'hours');

const exam = {
    id: 1,
    title: 'Test Exam',
    visibleDate: visibleDate,
    startDate: startDate,
    endDate: endDate,
    publishResultsDate: publishResultsDate,
    examStudentReviewStart: reviewStartDate,
    examStudentReviewEnd: reviewEndDate,
} as Exam;

const textResult = { id: 1, score: 50 } as Result;
const quizResult = { id: 2, score: 20 } as Result;
const modelingResult = { id: 3, score: 33.33 } as Result;
const programmingResult = { id: 4 } as Result;

const user = { id: 1, name: 'Test User' } as User;

const textParticipation = { id: 1, student: user, results: [textResult] } as StudentParticipation;
const quizParticipation = { id: 2, student: user, results: [quizResult] } as StudentParticipation;
const modelingParticipation = { id: 3, student: user, results: [modelingResult] } as StudentParticipation;
const programmingParticipation = { id: 4, student: user, results: [programmingResult] } as StudentParticipation;

const textExercise = { id: 1, title: 'Text Exercise', type: ExerciseType.TEXT, studentParticipations: [textParticipation], maxScore: 10 } as TextExercise;
const quizExercise = { id: 2, title: 'Quiz Exercise', type: ExerciseType.QUIZ, studentParticipations: [quizParticipation], maxScore: 10 } as QuizExercise;
const modelingExercise = { id: 3, title: 'Modeling Exercise', type: ExerciseType.MODELING, studentParticipations: [modelingParticipation], maxScore: 10 } as ModelingExercise;
const programmingExercise = {
    id: 4,
    title: 'Programming Exercise',
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    maxScore: 10,
} as ProgrammingExercise;
const exercises = [textExercise, quizExercise, modelingExercise, programmingExercise];

describe('ExamPointsSummaryComponent', function () {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), HttpClientTestingModule],
            declarations: [ExamPointsSummaryComponent, MockPipe(TranslatePipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamPointsSummaryComponent);
                component = fixture.componentInstance;
                component.exam = exam;
                component.exercises = exercises;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize and calculate scores correctly', function () {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.calculateAchievedPoints(textExercise)).to.equal(5);
        expect(component.calculateAchievedPoints(quizExercise)).to.equal(2);
        expect(component.calculateAchievedPoints(modelingExercise)).to.equal(3.3);
        expect(component.calculateAchievedPoints(programmingExercise)).to.equal(0);

        expect(component.calculatePointsSum()).to.equal(10.3);

        expect(component.calculateMaxPointsSum()).to.equal(40);
    });
});
