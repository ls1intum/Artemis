import fs from 'fs';
import path from 'path';
import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { drag, getExercise } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;
const background = fs.readFileSync(path.resolve(__dirname, '../../fixtures/exercise/quiz/drag_and_drop/background.jpg'));

/**
 * A drag and drop question in a live exam: two items belong to two drop locations on a background image. The student puts the first item where it
 * belongs and the second item into the wrong location, so one of the two mappings is right and the quiz is worth half of its points.
 */
test.describe('Exam quiz with a drag and drop question', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The mapping of the student is stored and scored per mapping', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        examStartEnd,
        courseAssessment,
    }) => {
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, examMaxPoints: 10 });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const quiz = await exerciseAPIRequests.createQuizExercise({
            body: { exerciseGroup },
            quizQuestions: [
                {
                    type: 'drag-and-drop',
                    title: 'Where do the items belong?',
                    text: 'Put each item where it belongs.',
                    points: 10,
                    scoringType: 'PROPORTIONAL_WITHOUT_PENALTY',
                    randomizeOrder: false,
                    backgroundFilePath: 'background.jpg',
                    dropLocations: [
                        { tempID: 1, posX: 10, posY: 10, width: 60, height: 60 },
                        { tempID: 2, posX: 120, posY: 10, width: 60, height: 60 },
                    ],
                    dragItems: [
                        { tempID: 11, text: 'Item A' },
                        { tempID: 12, text: 'Item B' },
                    ],
                    correctMappings: [
                        { dragItem: { tempID: 11 }, dropLocation: { tempID: 1 } },
                        { dragItem: { tempID: 12 }, dropLocation: { tempID: 2 } },
                    ],
                },
            ],
            backgroundFile: { name: 'background.jpg', mimeType: 'image/jpeg', buffer: background },
        });
        const question = quiz.quizQuestions![0] as unknown as { id: number; dragItems: { id: number; text: string }[]; dropLocations: { id: number }[] };
        expect(question.dragItems, 'both drag items were stored').toHaveLength(2);
        expect(question.dropLocations, 'both drop locations were stored').toHaveLength(2);
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        // The student puts Item A into the first location, where it belongs, and leaves Item B where it is.
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        const exercise = getExercise(page, quiz.id!);
        const locations = exercise.getByTestId('drop-location');
        await expect(locations).toHaveCount(2);
        await drag(page, exercise.locator('[id^="drag-item-"]', { hasText: 'Item A' }), locations.first());
        await expect(locations.first()).toContainText('Item A');
        await expect(locations.last()).not.toContainText('Item B');
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examNavigation.handInEarly();
        await examStartEnd.finishExam();

        // The server stored both mappings.
        const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
        const submission = summary.exercises![0].studentParticipations![0].submissions![0] as {
            submittedAnswers: { quizQuestion: { id: number }; mappings?: { dragItem: { id: number }; dropLocation: { id: number } }[] }[];
        };
        const mappings = submission.submittedAnswers.find((answer) => answer.quizQuestion.id === question.id)!.mappings ?? [];
        expect(mappings, 'the one item that was placed is stored').toHaveLength(1);
        expect(mappings[0].dragItem.id).toBe(question.dragItems.find((item) => item.text === 'Item A')!.id);
        expect(mappings[0].dropLocation.id).toBe(question.dropLocations[0].id);

        // The quiz is evaluated after the exam: the score follows the number of correct mappings.
        await login(admin);
        await examAPIRequests.concludeExam(exam);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
        expect((await courseAssessment.clickEvaluateQuizzes()).status()).toBe(200);
        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        const studentExamId = await examAPIRequests.getOwnStudentExamId(exam);
        const gradeSummary = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/grade-summary`);
        expect(gradeSummary.status()).toBe(200);
        expect((await gradeSummary.json()).studentResult.overallPointsAchieved, 'one of the two mappings is correct').toBe(5);
    });
});
