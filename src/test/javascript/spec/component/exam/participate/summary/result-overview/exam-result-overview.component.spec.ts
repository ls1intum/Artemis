import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { ExamResultOverviewComponent } from 'app/exam/participate/summary/result-overview/exam-result-overview.component';
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
import { ExerciseResult, StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { GradingKeyTableComponent } from 'app/grading-system/grading-key-overview/grading-key/grading-key-table.component';
import { CollapsibleCardComponent } from 'app/exam/participate/summary/collapsible-card.component';

let fixture: ComponentFixture<ExamResultOverviewComponent>;
let component: ExamResultOverviewComponent;
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

const textExerciseResult = {
    exerciseId: textExercise.id,
    achievedScore: 60,
    achievedPoints: 6,
    maxScore: textExercise.maxPoints,
} as ExerciseResult;

describe('ExamResultOverviewComponent', () => {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), HttpClientTestingModule],
            declarations: [
                ExamResultOverviewComponent,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(GradingKeyTableComponent),
                MockComponent(CollapsibleCardComponent),
            ],
            providers: [MockProvider(ExerciseService)],
        })
            .compileComponents()
            .then(() => {
                studentExamWithGrade = {
                    maxPoints: 40,
                    maxBonusPoints: 20,
                    studentExam: { exercises, exam, numberOfExamSessions: 0 },
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
                        exerciseGroupIdToExerciseResult: {
                            [textExercise.id!]: textExerciseResult,
                        },
                    },
                    achievedPointsPerExercise: {
                        [programmingExerciseTwo.id!]: 0,
                        [textExercise.id!]: 20,
                        [notIncludedTextExercise.id!]: 10,
                        [bonusTextExercise.id!]: 10,
                        [quizExercise.id!]: 2,
                        [modelingExercise.id!]: 3.33,
                        [programmingExercise.id!]: 0,
                    },
                };

                const course = new Course();
                course.id = 1;
                course.accuracyOfScores = 2;

                fixture = TestBed.createComponent(ExamResultOverviewComponent);
                component = fixture.componentInstance;
                exam.course = course;
                component.gradingScaleExists = false;
                component.studentExamWithGrade = studentExamWithGrade;
                component.exerciseInfos = {};
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

        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[programmingExerciseTwo.id!]).toBe(0);
        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[textExercise.id!]).toBe(20);
        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[notIncludedTextExercise.id!]).toBe(10);
        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[bonusTextExercise.id!]).toBe(10);
        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[quizExercise.id!]).toBe(2);
        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[modelingExercise.id!]).toBe(3.33);
        expect(component.studentExamWithGrade?.achievedPointsPerExercise?.[programmingExercise.id!]).toBe(0);

        expect(component.overallAchievedPoints).toBe(35.33);
        expect(component.maxPoints).toBe(40);
        expect(component.studentExamWithGrade?.maxBonusPoints).toBe(20);
        expect(component.getMaxNormalAndBonusPointsSum()).toBe(60);
    });

    it('should display 0 if no exercises are present', () => {
        component.studentExamWithGrade.studentExam!.exercises = [];
        component.studentExamWithGrade.maxPoints = 0;
        component.studentExamWithGrade.studentResult.overallPointsAchieved = 0;

        fixture.detectChanges();
        expect(fixture).not.toBeNull();

        expect(component.overallAchievedPoints).toBe(0);
        expect(component.maxPoints).toBe(0);
        expect(component.studentExamWithGrade?.maxBonusPoints).toBe(20);
        expect(component.getMaxNormalAndBonusPointsSum()).toBe(20);
    });

    describe('should evaluate showIncludedInScoreColumn', () => {
        it('to false if all exercises are included in the score', () => {
            const onlyIncludedExercises = [textExercise, quizExercise, modelingExercise, programmingExercise];
            component.studentExamWithGrade.studentExam!.exercises = onlyIncludedExercises;

            expect(component.containsExerciseThatIsNotIncludedCompletely()).toBeFalse();
        });

        it('to true if exercise is excluded', () => {
            const onlyIncludedExercises = [textExercise, quizExercise, modelingExercise, programmingExercise, notIncludedTextExercise];
            component.studentExamWithGrade.studentExam!.exercises = onlyIncludedExercises;

            expect(component.containsExerciseThatIsNotIncludedCompletely()).toBeTrue();
        });

        it('to true if bonus exercise is included', () => {
            const onlyIncludedExercises = [textExercise, quizExercise, modelingExercise, programmingExercise, bonusTextExercise];
            component.studentExamWithGrade.studentExam!.exercises = onlyIncludedExercises;

            expect(component.containsExerciseThatIsNotIncludedCompletely()).toBeTrue();
        });
    });

    describe('scrollToExercise', () => {
        it('should scroll to the target exercise dom element', () => {
            const mockElement = document.createElement('div');
            mockElement.id = 'exercise-1';
            document.body.appendChild(mockElement);
            mockElement.scrollIntoView = jest.fn();

            component.scrollToExercise(1);

            expect(mockElement.scrollIntoView).toHaveBeenCalledWith({
                behavior: 'smooth',
                block: 'start',
                inline: 'nearest',
            });
        });

        it('should log an error when the target exercise dom element does not exist', () => {
            const consoleErrorMock = jest.spyOn(console, 'error').mockImplementation();
            const INVALID_EXERCISE_ID = 999;

            component.scrollToExercise(INVALID_EXERCISE_ID);

            expect(consoleErrorMock).toHaveBeenCalledWith(expect.stringContaining('Cannot scroll to exercise, could not find exercise with corresponding id'));
        });

        it('should return immediately when exerciseId is undefined', () => {
            const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

            component.scrollToExercise(undefined);

            expect(consoleErrorSpy).not.toHaveBeenCalled();
        });
    });

    describe('summedAchievedExerciseScorePercentage', () => {
        it('should be called when overallScoreAchieved is not defined in DTO from server', () => {
            //@ts-ignore spying on private method
            const summedAchievedExerciseScorePercentageSpy = jest.spyOn(component, 'summedAchievedExerciseScorePercentage');
            component.studentExamWithGrade.studentResult.overallScoreAchieved = undefined;
            component.exerciseInfos = {};

            component.ngOnInit();

            expect(summedAchievedExerciseScorePercentageSpy).toHaveBeenCalledOnce();
        });

        it('should be called when overallScoreAchieved is 0 (default value, might be set as initial value because not defined from server DTO)', () => {
            //@ts-ignore spying on private method
            const summedAchievedExerciseScorePercentageSpy = jest.spyOn(component, 'summedAchievedExerciseScorePercentage');
            component.studentExamWithGrade.studentResult.overallScoreAchieved = 0;
            component.exerciseInfos = {};

            component.ngOnInit();

            expect(summedAchievedExerciseScorePercentageSpy).toHaveBeenCalledOnce();
        });

        it('should calculate achieved percentage from exercise info properly', () => {
            //@ts-ignore spying on private method
            const summedAchievedExerciseScorePercentageSpy = jest.spyOn(component, 'summedAchievedExerciseScorePercentage');
            const exerciseInfosWithAchievedPercentage = {
                1: { achievedPercentage: 80 },
                2: { achievedPercentage: 60 },
                3: { achievedPercentage: 90 },
            };
            //@ts-ignore missing attributes
            component.exerciseInfos = exerciseInfosWithAchievedPercentage;
            component.studentExamWithGrade.studentResult.overallScoreAchieved = undefined;

            component.ngOnInit();

            expect(summedAchievedExerciseScorePercentageSpy).toHaveBeenCalledOnce();
            expect(component.overallAchievedPercentageRoundedByCourseSettings).toBe(76.67);
        });
    });
});
