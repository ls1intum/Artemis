import { Course } from 'app/entities/course.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';

test.describe('Quiz Exercise Assessment', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
    });

    test.describe('MC Quiz assessment', () => {
        test.describe.configure({ retries: 2 });
        test('Assesses a mc quiz submission automatically', async ({ login, page, exerciseAPIRequests, exerciseResult }) => {
            await login(admin);
            const quizExercise = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceQuizTemplate], undefined, undefined, 10);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne);
            await exerciseAPIRequests.startExerciseParticipation(quizExercise.id!);
            await exerciseAPIRequests.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            await page.goto(`/courses/${course.id}/exercises/${quizExercise.id}`);
            await exerciseResult.shouldShowScore(50);
        });
    });

    test.describe('SA Quiz assessment', () => {
        test.describe.configure({ retries: 2 });
        test('Assesses a sa quiz submission automatically', async ({ login, page, exerciseAPIRequests, exerciseResult }) => {
            await login(admin);
            const quizExercise = await exerciseAPIRequests.createQuizExercise({ course }, [shortAnswerQuizTemplate], undefined, undefined, 10);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne);
            await exerciseAPIRequests.startExerciseParticipation(quizExercise.id!);
            await exerciseAPIRequests.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert']);
            await page.goto(`/courses/${course.id}/exercises/${quizExercise.id}`);
            await exerciseResult.shouldShowScore(66.7);
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
