import { getExamExercises, isExamResultPublished } from 'app/exam/participate/exam.utils';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { MockArtemisServerDateService } from '../../../helpers/mocks/service/mock-server-date.service';
import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { StudentExam } from 'app/entities/student-exam.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { QuizExamSubmission } from 'app/entities/quiz/quiz-exam-submission.model';

let artemisServerDateService: ArtemisServerDateService;

describe('ExamUtils', () => {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: ArtemisServerDateService, useClass: MockArtemisServerDateService }],
        })
            .compileComponents()
            .then(() => {
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
            });
    });

    describe('isExamResultPublished', () => {
        it('should always be true for test runs', () => {
            const isTestRun = true;
            const exam = undefined;

            const resultsArePublished = isExamResultPublished(isTestRun, exam, artemisServerDateService);
            expect(resultsArePublished).toBeTrue();
        });

        it('should be false if publishReleaseDate is in the future', () => {
            const isTestRun = false;
            const dateInFuture = dayjs().add(5, 'days');
            const exam = { publishResultsDate: dateInFuture } as Exam;

            const resultsArePublished = isExamResultPublished(isTestRun, exam, artemisServerDateService);
            expect(resultsArePublished).toBeFalse();
        });

        it('should be true if publishReleaseDate is in the past', () => {
            const isTestRun = false;
            const dateInPast = dayjs().subtract(2, 'days');
            const exam = { publishResultsDate: dateInPast } as Exam;

            const resultsArePublished = isExamResultPublished(isTestRun, exam, artemisServerDateService);
            expect(resultsArePublished).toBeTrue();
        });
    });

    describe('getExamExercises', () => {
        it('should return the exam exercises if studentExam has exercises', () => {
            const studentExam = new StudentExam();
            const course = new Course();
            studentExam.exercises = [new QuizExercise(course, undefined), new TextExercise(course, undefined)];
            const examExercises = getExamExercises(studentExam, { title: 'Quiz Exam', navigationTitle: 'Quiz' });
            expect(examExercises).toEqual(studentExam.exercises);
        });

        it('should return the exam exercises with quiz exam if studentExam has quiz exam', () => {
            const studentExam = new StudentExam();
            const exam = new Exam();
            exam.quizExamMaxPoints = 100;
            exam.randomizeQuizExamQuestionsOrder = true;
            studentExam.exam = exam;
            studentExam.quizQuestions = [new MultipleChoiceQuestion(), new DragAndDropQuestion()];
            const examExercises = getExamExercises(studentExam, { title: 'Quiz Exam', navigationTitle: 'Quiz' });
            const submission = new QuizExamSubmission();
            submission.isSynced = true;
            submission.studentExam = studentExam;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.title = 'Quiz Exam';
            expect(examExercises).toHaveLength(1);
            expect(examExercises[0]).toEqual({
                id: 0,
                type: ExerciseType.QUIZ,
                studentParticipations: [
                    {
                        initializationState: InitializationState.INITIALIZED,
                        submissions: [submission],
                    },
                ],
                navigationTitle: 'Quiz',
                overviewTitle: 'Quiz Exam',
                exerciseGroup: exerciseGroup,
                title: 'Quiz Exam',
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                quizQuestions: studentExam.quizQuestions,
                maxPoints: exam.quizExamMaxPoints,
                randomizeQuestionOrder: exam.randomizeQuizExamQuestionsOrder,
            });
        });

        it('should return empty exam exercises if studentExam has no exercises and no quiz exam', () => {
            const studentExam = new StudentExam();
            const examExercises = getExamExercises(studentExam, { title: 'Quiz Exam', navigationTitle: 'Quiz' });
            expect(examExercises).toEqual([]);
        });
    });
});
