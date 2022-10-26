import dayjs from 'dayjs/esm';
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
import { GradeType } from 'app/entities/grading-scale.model';
import { Course } from 'app/entities/course.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';

let fixture: ComponentFixture<ExamPointsSummaryComponent>;
let component: ExamPointsSummaryComponent;
let studentExamWithGrade: StudentExamWithGradeDTO;

const visibleDate = dayjs().subtract(7, 'hours');
const startDate = dayjs().subtract(6, 'hours');
const endDate = dayjs().subtract(5, 'hours');
const publishResultsDate = dayjs().subtract(3, 'hours');
const reviewStartDate = dayjs().subtract(2, 'hours');
const reviewEndDate = dayjs().add(1, 'hours');

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

describe('ExamPointsSummaryComponent', () => {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), HttpClientTestingModule],
            declarations: [ExamPointsSummaryComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ExerciseService)],
        })
            .compileComponents()
            .then(() => {
                studentExamWithGrade = {
                    maxPoints: 40,
                    maxBonusPoints: 20,
                    studentExam: { exercises, exam },
                    studentResult: {
                        userId: 1,
                        name: 'user1',
                        login: 'user1',
                        email: 'user1@tum.de',
                        registrationNumber: '111',
                        overallPointsAchieved: 35.33,
                        overallScoreAchieved: (35.33 / 40) * 100,
                        overallPointsAchievedInFirstCorrection: 45,
                        overallGrade: '1.7',
                        hasPassed: true,
                        submitted: true,
                        exerciseGroupIdToExerciseResult: {},
                    },
                    achievedPointsPerExercise: {
                        [textExercise.id!]: 20,
                        [bonusTextExercise.id!]: 10,
                        [notIncludedTextExercise.id!]: 10,
                        [quizExercise.id!]: 2,
                        [modelingExercise.id!]: 3.33,
                        [programmingExercise.id!]: 0,
                    },
                };

                const course = new Course();
                course.id = 1;
                course.accuracyOfScores = 2;

                fixture = TestBed.createComponent(ExamPointsSummaryComponent);
                component = fixture.componentInstance;
                exam.course = course;
                component.gradingScaleExists = false;
                component.studentExamWithGrade = studentExamWithGrade;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should handle error correctly', () => {
        component.studentExamWithGrade.studentExam = undefined as any;
        fixture.detectChanges();

        expect(fixture).not.toBeNull();
        expect(component.studentExamWithGrade.studentExam).toBeUndefined();
        expect(component.gradingScaleExists).toBeFalse();
    });

    it('should retrieve exam grade correctly', () => {
        fixture.detectChanges();

        expect(fixture).not.toBeNull();
        expect(component.gradingScaleExists).toBeTrue();
        expect(component.isBonus).toBeFalse();
        expect(component.grade).toEqual(studentExamWithGrade.studentResult.overallGrade);
        expect(component.isBonus).toEqual(studentExamWithGrade.gradeType === GradeType.BONUS);
        expect(component.hasPassed).toEqual(studentExamWithGrade.studentResult.hasPassed);
    });

    it('should initialize and calculate scores correctly', () => {
        fixture.detectChanges();
        expect(fixture).not.toBeNull();
        expect(component.getAchievedPoints(programmingExerciseTwo)).toBe(0);
        expect(component.getAchievedPoints(textExercise)).toBe(20);
        expect(component.getAchievedPoints(notIncludedTextExercise)).toBe(10);
        expect(component.getAchievedPoints(bonusTextExercise)).toBe(10);
        expect(component.getAchievedPoints(quizExercise)).toBe(2);
        expect(component.getAchievedPoints(modelingExercise)).toBe(3.33);
        expect(component.getAchievedPoints(programmingExercise)).toBe(0);

        expect(component.getAchievedPointsSum()).toBe(35.33);
        expect(component.getMaxNormalPointsSum()).toBe(40);
        expect(component.getMaxBonusPointsSum()).toBe(20);
    });

    it('should display 0 if no exercises are present', () => {
        component.studentExamWithGrade.studentExam!.exercises = [];
        component.studentExamWithGrade.maxPoints = 0;
        component.studentExamWithGrade.studentResult.overallPointsAchieved = 0;

        fixture.detectChanges();
        expect(fixture).not.toBeNull();

        expect(component.getAchievedPointsSum()).toBe(0);
        expect(component.getMaxNormalPointsSum()).toBe(0);
    });
});
