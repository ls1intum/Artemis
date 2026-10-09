import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import {
    toQuizExercise,
    toQuizExerciseFromListRow,
    toQuizPointStatistics,
    toQuizQuestionStatistic,
    toQuizStatisticsOverview,
    toQuizSubmissionFromStudent,
    toResult,
} from 'app/quiz/shared/util/generated-quiz-exercise.util';
import { QuizExerciseWithoutQuestions } from 'app/openapi/model/quiz-exercise-without-questions';
import { QuizExerciseForCourse } from 'app/openapi/model/quiz-exercise-for-course';
import { QuizStatisticsOverview } from 'app/openapi/model/quiz-statistics-overview';
import { QuizPointStatistics } from 'app/openapi/model/quiz-point-statistics';
import { QuizQuestionStatisticResponse } from 'app/openapi/model/quiz-question-statistic-response';
import { ResultAfterEvaluationWithSubmission } from 'app/openapi/model/result-after-evaluation-with-submission';
import { QuizExerciseDetails } from 'app/openapi/model/quiz-exercise-details';

const RELEASE = '2026-09-01T08:00:00Z';
const START = '2026-09-02T09:00:00Z';
const DUE = '2026-09-02T10:00:00Z';

const expectSameInstant = (actual: dayjs.Dayjs | undefined, expected: string) => {
    expect(dayjs.isDayjs(actual)).toBe(true);
    expect(actual!.toISOString()).toBe(dayjs(expected).toISOString());
};

describe('toQuizExercise', () => {
    it('should convert dates, course, batches and the variant group timeline', () => {
        const exercise: QuizExerciseWithoutQuestions = {
            id: 1,
            title: 'Quiz',
            releaseDate: RELEASE,
            startDate: START,
            dueDate: DUE,
            course: { id: 7, title: 'Course' },
            quizBatches: [{ id: 91, startTime: START, started: true }],
            exerciseVariantGroup: { id: 5, title: 'Group', maxPoints: 10, releaseDate: RELEASE, startDate: START, dueDate: DUE },
        };

        const converted = toQuizExercise(exercise);

        expect(converted).toBeInstanceOf(QuizExercise);
        expectSameInstant(converted.releaseDate, RELEASE);
        expectSameInstant(converted.startDate, START);
        expectSameInstant(converted.dueDate, DUE);
        expect(converted.assessmentDueDate).toBeUndefined();
        expect(converted.course).toBeInstanceOf(Course);
        expect(converted.course!.id).toBe(7);
        expect(converted.quizBatches![0]).toBeInstanceOf(QuizBatch);
        expectSameInstant(converted.quizBatches![0].startTime, START);
        // A string date in the group would be written back as missing and wipe the shared timeline of its variants.
        const group = converted.exerciseVariantGroup!;
        expect(group.id).toBe(5);
        expect(group.maxPoints).toBe(10);
        expectSameInstant(group.releaseDate, RELEASE);
        expectSameInstant(group.startDate, START);
        expectSameInstant(group.dueDate, DUE);
        expect(group.assessmentDueDate).toBeUndefined();
    });

    it('should leave the questions absent while the server withholds them', () => {
        const converted = toQuizExercise({ id: 1, title: 'Not started' });

        expect(converted.quizQuestions).toBeUndefined();
        expect(converted.course).toBeUndefined();
        expect(converted.exerciseVariantGroup).toBeUndefined();
    });

    it('should leave editability undetermined when the server omits it, so callers compute it', () => {
        const converted = toQuizExercise({ id: 1, title: 'Saved' });

        expect(converted.isEditable).toBeUndefined();
    });

    it('should keep the editability the server sends', () => {
        const details: QuizExerciseDetails = { id: 1, title: 'Not editable', isEditable: false };

        expect(toQuizExercise(details).isEditable).toBe(false);
        expect(toQuizExercise({ ...details, isEditable: true }).isEditable).toBe(true);
    });
});

describe('toQuizExerciseFromListRow', () => {
    it('should convert the dates and batches of a list row', () => {
        const row: QuizExerciseForCourse = {
            id: 2,
            title: 'Row',
            quizMode: 'SYNCHRONIZED',
            includedInOverallScore: 'INCLUDED_COMPLETELY',
            releaseDate: RELEASE,
            startDate: START,
            dueDate: DUE,
            quizBatches: [{ id: 92, startTime: START, password: 'secret' }],
        };

        const converted = toQuizExerciseFromListRow(row);

        expect(converted).toBeInstanceOf(QuizExercise);
        expect(converted.title).toBe('Row');
        expectSameInstant(converted.releaseDate, RELEASE);
        expectSameInstant(converted.startDate, START);
        expectSameInstant(converted.dueDate, DUE);
        expect(converted.quizBatches![0]).toBeInstanceOf(QuizBatch);
        expect(converted.quizBatches![0].password).toBe('secret');
        expectSameInstant(converted.quizBatches![0].startTime, START);
    });
});

describe('quiz statistics', () => {
    it('should keep the per-question summaries and participant counts of the overview', () => {
        const overview: QuizStatisticsOverview = {
            id: 1,
            dueDate: DUE,
            participantsRated: 4,
            participantsUnrated: 1,
            quizQuestions: [{ id: 3, title: 'Q', points: 2, quizQuestionStatistic: { type: 'multiple-choice', participantsRated: 4 } }],
        };

        const converted = toQuizStatisticsOverview(overview);

        expectSameInstant(converted.dueDate, DUE);
        expect(converted.participantsRated).toBe(4);
        expect(converted.participantsUnrated).toBe(1);
        expect(converted.quizQuestions).toEqual(overview.quizQuestions);
    });

    it('should convert the questions of the point statistics and keep the point distribution', () => {
        const pointStatistics: QuizPointStatistics = {
            id: 1,
            startDate: START,
            quizQuestions: [{ type: 'multiple-choice', id: 3 }],
            quizPointStatistic: { participantsRated: 2, pointCounters: [{ points: 1, ratedCounter: 2 }] },
        };

        const converted = toQuizPointStatistics(pointStatistics);

        expectSameInstant(converted.startDate, START);
        expect(converted.quizQuestions![0]).toBeInstanceOf(MultipleChoiceQuestion);
        expect(converted.quizPointStatistic).toEqual(pointStatistics.quizPointStatistic);
    });

    it('should convert the question of a question statistic and keep its counters', () => {
        const questionStatistic: QuizQuestionStatisticResponse = {
            id: 1,
            quizQuestion: { type: 'multiple-choice', id: 3, title: 'Q' },
            quizQuestionStatistic: { type: 'multiple-choice', answerCounters: [{ answerId: 71, ratedCounter: 3 }] },
        };

        const converted = toQuizQuestionStatistic(questionStatistic);

        expect(converted.quizQuestion).toBeInstanceOf(MultipleChoiceQuestion);
        expect(converted.quizQuestion!.title).toBe('Q');
        expect(converted.quizQuestionStatistic).toEqual(questionStatistic.quizQuestionStatistic);
        expect(toQuizQuestionStatistic({ id: 1 }).quizQuestion).toBeUndefined();
    });
});

describe('toResult', () => {
    it('should convert the submission of a result together with its participation', () => {
        const result: ResultAfterEvaluationWithSubmission = {
            id: 101,
            completionDate: DUE,
            score: 50,
            submission: {
                id: 111,
                submissionDate: DUE,
                submittedAnswers: [{ type: 'multiple-choice', id: 81, scoreInPoints: 1 }],
                participation: {
                    quizQuestionsType: 'after-quiz-end',
                    id: 121,
                    initializationDate: RELEASE,
                    student: { id: 131 },
                    exercise: { id: 1, dueDate: DUE, quizQuestions: [{ type: 'multiple-choice', id: 3 }] },
                },
            },
        };

        const converted = toResult(result);

        expectSameInstant(converted.completionDate, DUE);
        const submission = converted.submission as QuizSubmission;
        expect(submission).toBeInstanceOf(QuizSubmission);
        expectSameInstant(submission.submissionDate, DUE);
        expect(submission.submittedAnswers![0]).toBeInstanceOf(MultipleChoiceSubmittedAnswer);
        const participation = submission.participation as StudentParticipation;
        expect(participation).toBeInstanceOf(StudentParticipation);
        expectSameInstant(participation.initializationDate, RELEASE);
        expect(participation.student).toBeInstanceOf(User);
        expect(participation.student!.id).toBe(131);
        expect(participation.exercise).toBeInstanceOf(QuizExercise);
        expect((participation.exercise as QuizExercise).quizQuestions![0]).toBeInstanceOf(MultipleChoiceQuestion);
    });

    it('should leave the submission absent when the result carries none', () => {
        expect(toResult({ id: 101, score: 0 }).submission).toBeUndefined();
    });
});

describe('toQuizSubmissionFromStudent', () => {
    it('should send an empty answer list for a submission without answers', () => {
        expect(toQuizSubmissionFromStudent(new QuizSubmission())).toEqual({ submittedAnswers: [] });
    });
});
