import { test } from '../../support/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { expect } from '@playwright/test';
import { admin, studentTwo } from '../../support/users';
import { generateUUID, getExercise } from '../../support/utils';
import dayjs from 'dayjs';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';

/**
 * CRITICAL REGRESSION TEST: free-text exam summary isolation (issue #12937).
 *
 * This test guards against a production bug where, with multiple free-text (TEXT) exercises in an exam,
 * the student summary showed the problem statement and answer of only the LAST text exercise for EVERY
 * text exercise. Assessment and the database were correct; the bug was purely in the summary UI.
 *
 * Root cause: every <jhi-text-editor> instance subscribed to the app-wide shared participation
 * BehaviorSubject (ParticipationWebsocketService.subscribeForParticipationChanges) and applied EVERY
 * emission to its own state. When the last editor called addParticipation(...) on init, that emission
 * overwrote the exercise + submission of all the other editors, so every text summary converged on the
 * last exercise. The fix makes each editor ignore participation changes that are not its own.
 *
 * This test creates 3 TEXT exercises with DISTINCT problem statements and the student types a DISTINCT
 * answer into each. On the summary it verifies that each exercise shows its OWN answer and problem
 * statement, not another exercise's. Before the fix, exercises A and B would show exercise C's content.
 */

// Distinct, easily distinguishable markers so cross-contamination is unambiguous.
const PROBLEM_STATEMENT_A = '# Exercise Alpha\n\nALPHA_PROBLEM_STATEMENT_MARKER: describe the alpha algorithm.';
const PROBLEM_STATEMENT_B = '# Exercise Beta\n\nBETA_PROBLEM_STATEMENT_MARKER: describe the beta algorithm.';
const PROBLEM_STATEMENT_C = '# Exercise Gamma\n\nGAMMA_PROBLEM_STATEMENT_MARKER: describe the gamma algorithm.';

const ANSWER_A = 'ALPHA_ANSWER_MARKER: my answer to the alpha exercise.';
const ANSWER_B = 'BETA_ANSWER_MARKER: my answer to the beta exercise.';
const ANSWER_C = 'GAMMA_ANSWER_MARKER: my answer to the gamma exercise.';

const course = { id: SEED_COURSES.examParticipation.id } as any;

test.describe('Exam free-text summary isolation', { tag: '@slow' }, () => {
    let exam: Exam;
    let exerciseA: TextExercise;
    let exerciseB: TextExercise;
    let exerciseC: TextExercise;
    let groupTitleA: string;
    let groupTitleB: string;
    let groupTitleC: string;

    test.beforeEach('Create exam with 3 text exercises', async ({ login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);

        exam = await createExam(course, examAPIRequests, {
            title: 'Text Summary Isolation ' + generateUUID(),
            examMaxPoints: 30,
            numberOfExercisesInExam: 3,
        });

        // Create 3 exercise groups with text exercises, each with a DISTINCT problem statement.
        groupTitleA = 'Group Alpha ' + generateUUID();
        const exerciseGroupA = await examAPIRequests.addExerciseGroupForExam(exam, groupTitleA);
        exerciseA = await exerciseAPIRequests.createTextExercise({ exerciseGroup: exerciseGroupA }, 'Exercise Alpha ' + generateUUID(), {
            ...textExerciseTemplate,
            problemStatement: PROBLEM_STATEMENT_A,
        });

        groupTitleB = 'Group Beta ' + generateUUID();
        const exerciseGroupB = await examAPIRequests.addExerciseGroupForExam(exam, groupTitleB);
        exerciseB = await exerciseAPIRequests.createTextExercise({ exerciseGroup: exerciseGroupB }, 'Exercise Beta ' + generateUUID(), {
            ...textExerciseTemplate,
            problemStatement: PROBLEM_STATEMENT_B,
        });

        groupTitleC = 'Group Gamma ' + generateUUID();
        const exerciseGroupC = await examAPIRequests.addExerciseGroupForExam(exam, groupTitleC);
        exerciseC = await exerciseAPIRequests.createTextExercise({ exerciseGroup: exerciseGroupC }, 'Exercise Gamma ' + generateUUID(), {
            ...textExerciseTemplate,
            problemStatement: PROBLEM_STATEMENT_C,
        });

        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test('shows each text exercise its own problem statement and answer in the summary', async ({ page, examParticipation, examNavigation, examStartEnd, textExerciseEditor }) => {
        await examParticipation.startParticipation(studentTwo, course, exam);

        // Type a DISTINCT answer into each text exercise. Opening the next exercise triggers a save of the previous one.
        await examNavigation.openOrSaveExerciseByTitle(groupTitleA);
        await textExerciseEditor.typeSubmission(exerciseA.id!, ANSWER_A);
        await examNavigation.openOrSaveExerciseByTitle(groupTitleB);
        await textExerciseEditor.typeSubmission(exerciseB.id!, ANSWER_B);
        await examNavigation.openOrSaveExerciseByTitle(groupTitleC);
        await textExerciseEditor.typeSubmission(exerciseC.id!, ANSWER_C);

        await examParticipation.handInEarly();
        await examStartEnd.pressShowSummary();

        // Each exercise must show its OWN answer in its OWN summary card (scoped via #exercise-{id}).
        // Before the fix, every text editor showed the LAST exercise's answer (ANSWER_C) here.
        await expect(getExercise(page, exerciseA.id!).locator('#text-editor')).toHaveValue(ANSWER_A, { timeout: 20000 });
        await expect(getExercise(page, exerciseB.id!).locator('#text-editor')).toHaveValue(ANSWER_B, { timeout: 20000 });
        await expect(getExercise(page, exerciseC.id!).locator('#text-editor')).toHaveValue(ANSWER_C, { timeout: 20000 });

        // Each exercise must also show its OWN problem statement (and not another exercise's), which the
        // shared-subject bug equally corrupted.
        await expect(getExercise(page, exerciseA.id!).locator('.markdown-preview')).toContainText('ALPHA_PROBLEM_STATEMENT_MARKER', { timeout: 20000 });
        await expect(getExercise(page, exerciseB.id!).locator('.markdown-preview')).toContainText('BETA_PROBLEM_STATEMENT_MARKER', { timeout: 20000 });
        await expect(getExercise(page, exerciseC.id!).locator('.markdown-preview')).toContainText('GAMMA_PROBLEM_STATEMENT_MARKER', { timeout: 20000 });

        // Explicit cross-contamination guard: A's problem-statement panel must not contain B's or C's markers.
        // (#exercise-{id} intentionally matches both the summary card and the nested text-editor div, so scope the
        // negative check to the unique .markdown-preview to avoid a strict-mode multiple-element match.)
        await expect(getExercise(page, exerciseA.id!).locator('.markdown-preview')).not.toContainText('BETA_PROBLEM_STATEMENT_MARKER');
        await expect(getExercise(page, exerciseA.id!).locator('.markdown-preview')).not.toContainText('GAMMA_PROBLEM_STATEMENT_MARKER');
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
