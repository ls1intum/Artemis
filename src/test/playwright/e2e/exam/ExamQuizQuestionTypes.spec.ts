import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { getExercise } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import multipleChoiceTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerTemplate from '../../fixtures/exercise/quiz/short_answer/template.json';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * One quiz of an exam with a multiple choice question worth four points and a short answer question worth six points. The student ticks both correct
 * options and fills three of the six gaps correctly (and one gap wrongly), so the quiz is worth 4 + 3 = 7 of 10 points after the quiz evaluation. The
 * answers of both question types must reach the server, and the points must be calculated per question type.
 */
test.describe('Exam quiz with several question types', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Answers of a multiple choice and a short answer question are stored and scored', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        examStartEnd,
        quizExerciseMultipleChoice,
        courseAssessment,
    }) => {
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, examMaxPoints: 10 });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const quiz = await exerciseAPIRequests.createQuizExercise({
            body: { exerciseGroup },
            quizQuestions: [
                { ...multipleChoiceTemplate, points: 4 },
                { ...shortAnswerTemplate, points: 6 },
            ],
        });
        expect(quiz.maxPoints, 'the points of the two questions add up').toBe(10);
        const multipleChoice = quiz.quizQuestions!.find((question) => question.type === 'multiple-choice')!;
        const shortAnswer = quiz.quizQuestions!.find((question) => question.type === 'short-answer')!;
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        // The student ticks the two correct options and fills in three gaps correctly and one wrongly.
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await quizExerciseMultipleChoice.tickAnswerOption(quiz.id!, 0, multipleChoice.id);
        await quizExerciseMultipleChoice.tickAnswerOption(quiz.id!, 1, multipleChoice.id);
        const gaps = await getExercise(page, quiz.id!).locator(`[id^="solution-"][id$="-${shortAnswer.id}"]`).all();
        expect(gaps, 'the short answer question shows its six gaps').toHaveLength(6);
        for (const [index, answer] of ['give', 'let', 'run', 'wrong'].entries()) {
            await gaps[index].fill(answer);
        }
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examNavigation.handInEarly();
        await examStartEnd.finishExam();

        // The server stored both answers.
        const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
        const submission = summary.exercises![0].studentParticipations![0].submissions![0] as {
            submittedAnswers: { quizQuestion: { id: number }; selectedOptions?: unknown[]; submittedTexts?: { text: string }[] }[];
        };
        const answerOf = (questionId: number) => submission.submittedAnswers.find((answer) => answer.quizQuestion.id === questionId)!;
        expect(answerOf(multipleChoice.id!).selectedOptions, 'both ticked options are stored').toHaveLength(2);
        expect(
            answerOf(shortAnswer.id!)
                .submittedTexts!.map((submitted) => submitted.text)
                .sort(),
            'the four filled gaps are stored',
        ).toEqual(['give', 'let', 'run', 'wrong']);

        // The exam ends, the instructor evaluates the quiz, and the points are calculated per question.
        await login(admin);
        await examAPIRequests.concludeExam(exam);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
        expect((await courseAssessment.clickEvaluateQuizzes()).status()).toBe(200);

        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        await examParticipation.checkResultScore('70%');
        const studentExamId = await examAPIRequests.getOwnStudentExamId(exam);
        const gradeSummary = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/grade-summary`);
        expect(gradeSummary.status()).toBe(200);
        expect((await gradeSummary.json()).studentResult.overallPointsAchieved, '4 points for the multiple choice question and 3 for the short answer question').toBe(7);
    });
});
