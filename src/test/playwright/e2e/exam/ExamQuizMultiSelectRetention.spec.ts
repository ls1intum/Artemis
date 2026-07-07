import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { admin, studentTwo } from '../../support/users';
import { generateUUID, getExercise } from '../../support/utils';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise, ExerciseType } from '../../support/constants';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';

/**
 * Regression test for multiple-choice answer loss when adding to a SAVED selection in exam mode (issue #12938).
 *
 * The dangerous, deterministic variant of the bug: after a reload (or any component recreation mid-exam), the
 * previously selected options are loaded into the component's input from the saved submission, but the component's
 * local selection accumulator started empty and was never synced from that input. The next ADDITIVE click then
 * emitted only the newly clicked option, silently dropping every previously saved answer.
 *
 * This test selects one option, saves, RELOADS the exam, and then selects an ADDITIONAL option. It verifies that the
 * earlier answer is NOT lost - both options stay selected, and both survive a second save + reload. Before the fix,
 * the additive click after the reload dropped the first option.
 */
const course = { id: SEED_COURSES.examParticipation.id } as any;

test.describe('Exam quiz multiple-choice add-to-saved-selection retention', { tag: '@slow' }, () => {
    let exam: Exam;
    let quizExercise: Exercise;
    let questionId: number;

    test.beforeEach('Create exam with a multiple-choice quiz', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await createExam(course, examAPIRequests, { title: 'exam' + generateUUID() });
        quizExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
        // The seeded MC template has 4 answer options (2 correct, 2 wrong). additionalData.quizExerciseID is the question id.
        questionId = quizExercise.additionalData!.quizExerciseID!;
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test('does not drop a saved answer when adding another option after a reload', async ({ page, examParticipation, examNavigation, quizExerciseMultipleChoice }) => {
        const exerciseId = quizExercise.id!;
        const option = (i: number) => getExercise(page, exerciseId).locator(`#question${questionId} #answer-option-${i}`);

        await examParticipation.startParticipation(studentTwo, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);

        // Select the first option and save it (navigating away triggers the deterministic exam-submission PUT).
        await quizExerciseMultipleChoice.tickAnswerOption(exerciseId, 0, questionId);
        await expect(option(0)).toHaveClass(/selected/);
        const firstSave = page.waitForResponse((r) => r.url().includes(`/api/quiz/exercises/${exerciseId}/submissions/exam`) && r.request().method() === 'PUT' && r.ok(), {
            timeout: 30000,
        });
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);
        await firstSave;

        // Reload and re-enter: the saved selection [option 0] is now loaded into the input of a freshly created
        // component (whose local accumulator is empty - exactly the bug condition).
        await page.reload();
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await examParticipation.startExam();
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);
        await expect(option(0)).toHaveClass(/selected/, { timeout: 15000 });

        // Add a SECOND option after the reload. Before the fix this emitted only option 1 and dropped option 0.
        await quizExerciseMultipleChoice.tickAnswerOption(exerciseId, 1, questionId);

        // Both options must now be selected - the earlier saved answer must not have been dropped.
        await expect(option(0)).toHaveClass(/selected/);
        await expect(option(1)).toHaveClass(/selected/);

        // The additive selection must also persist across another save + reload.
        const secondSave = page.waitForResponse((r) => r.url().includes(`/api/quiz/exercises/${exerciseId}/submissions/exam`) && r.request().method() === 'PUT' && r.ok(), {
            timeout: 30000,
        });
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);
        await secondSave;
        await page.reload();
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await examParticipation.startExam();
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);

        await expect(option(0)).toHaveClass(/selected/, { timeout: 15000 });
        await expect(option(1)).toHaveClass(/selected/);
        // The untouched options must remain unselected.
        await expect(option(2)).not.toHaveClass(/selected/);
        await expect(option(3)).not.toHaveClass(/selected/);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });
});

async function createExam(course: any, examAPIRequests: ExamAPIRequests, customExamConfig?: any) {
    const defaultExamConfig = {
        course,
        title: 'exam' + generateUUID(),
        visibleDate: dayjs().subtract(3, 'minutes'),
        startDate: dayjs().subtract(2, 'minutes'),
        endDate: dayjs().add(1, 'hour'),
        examMaxPoints: 10,
        numberOfExercisesInExam: 1,
    };
    return await examAPIRequests.createExam({ ...defaultExamConfig, ...customExamConfig });
}
