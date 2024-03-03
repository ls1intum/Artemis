import { createQuizExam, isExamResultPublished } from 'app/exam/participate/exam.utils';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { MockArtemisServerDateService } from '../../../helpers/mocks/service/mock-server-date.service';
import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { StudentExam } from 'app/entities/student-exam.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExam } from 'app/entities/quiz-exam.model';
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

    describe('createQuizExam', () => {
        describe('createQuizExam', () => {
            it('should create a QuizExam object with the correct values when the student exam has quiz questions and non-zero quizExamMaxPoints value', () => {
                const studentExam: StudentExam = new StudentExam();
                const title: string = 'Quiz Exam';
                studentExam.exam = new Exam();
                studentExam.exam.quizExamMaxPoints = 10;
                studentExam.quizQuestions = [new MultipleChoiceQuestion(), new MultipleChoiceQuestion()];
                studentExam.exam.randomizeQuizExamQuestionsOrder = true;

                const quizExam: QuizExam | undefined = createQuizExam(studentExam, title);

                expect(quizExam).toBeDefined();
                expect(quizExam!.title).toEqual(title);
                expect(quizExam!.exerciseGroup!.title).toEqual(title);
                expect(quizExam!.quizQuestions).toEqual(studentExam.quizQuestions);
                expect(quizExam!.randomizeQuestionOrder).toEqual(studentExam.exam!.randomizeQuizExamQuestionsOrder);
                expect(quizExam!.maxPoints).toEqual(studentExam.exam!.quizExamMaxPoints);
            });

            it('should create a QuizExam object with the correct values when the student exam has quiz questions, non-zero quizExamMaxPoints value, and quizExamSubmission', () => {
                const studentExam: StudentExam = new StudentExam();
                const title: string = 'Quiz Exam';
                studentExam.id = 2;
                studentExam.exam = new Exam();
                studentExam.exam.quizExamMaxPoints = 10;
                studentExam.quizQuestions = [new MultipleChoiceQuestion(), new MultipleChoiceQuestion()];
                studentExam.exam.randomizeQuizExamQuestionsOrder = true;
                const quizExamSubmission = new QuizExamSubmission();
                quizExamSubmission.studentExam = new StudentExam();
                quizExamSubmission.studentExam.id = studentExam.id;
                studentExam.quizExamSubmission = quizExamSubmission;
                studentExam.quizExamSubmission.id = 1;

                const quizExam: QuizExam | undefined = createQuizExam(studentExam, title);

                expect(quizExam).toBeDefined();
                expect(quizExam!.title).toEqual(title);
                expect(quizExam!.exerciseGroup!.title).toEqual(title);
                expect(quizExam!.quizQuestions).toEqual(studentExam.quizQuestions);
                expect(quizExam!.randomizeQuestionOrder).toEqual(studentExam.exam!.randomizeQuizExamQuestionsOrder);
                expect(quizExam!.maxPoints).toEqual(studentExam.exam!.quizExamMaxPoints);
                expect(quizExam!.submission).toEqual(quizExamSubmission);
            });

            it('should return undefined when the student exam has a quizExamMaxPoints value of 0', () => {
                const studentExam: StudentExam = new StudentExam();
                const title: string = 'Quiz Exam';
                studentExam.exam = new Exam();
                studentExam.exam.quizExamMaxPoints = 0;

                const quizExam: QuizExam | undefined = createQuizExam(studentExam, title);

                expect(quizExam).toBeUndefined();
            });
        });
    });
});
