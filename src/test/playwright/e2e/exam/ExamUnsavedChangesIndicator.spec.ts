import { test } from '../../support/fixtures';
import { ExerciseType } from '../../support/constants';
import { admin, studentOne } from '../../support/users';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../support/seedData';
import { getExercise } from '../../support/utils';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * Regression test for the exam save-state indicators, which tell a student whether their answer has
 * reached the server. They are driven by `Submission.isSynced`, which every exam editor mutates **in
 * place** on a plain object. Under zoneless change detection that mutation schedules no re-render on its
 * own, so each producer calls `ExamParticipationService.notifySubmissionSyncStateChanged()` and each
 * consumer reads `submissionSyncVersion()`. When a consumer forgets to read it, the indicator silently
 * freezes: the student edits an answer and the UI keeps claiming it is saved until some unrelated click
 * happens to trigger change detection. During an exam that hides the unsaved-changes warning, which is
 * why this is worth an end-to-end test.
 *
 * Unit tests cannot catch it. They drive change detection explicitly, so a binding that never declares a
 * dependency on the version signal still appears to update. Only a real browser under real zoneless
 * change detection distinguishes the two, so this test asserts purely by polling the DOM and never
 * performs an unrelated interaction that could mask a missing re-render.
 *
 * Covers both consumers of the signal in the conduction view: the navigation sidebar's per-exercise
 * status icon (`getExerciseButtonStatus`, rendered as the `synced` / `synced saved` / `notSynced` state)
 * and the save button (`ExerciseSaveButtonComponent`, whose `disabled` state mirrors `isSynced`).
 */
test.describe('Exam unsaved changes indicator', { tag: '@slow' }, () => {
    let exam: any;
    let textExercise: any;

    test.beforeEach('Create an exam with a text exercise', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        textExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum.txt' });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('shows unsaved changes as soon as the student edits, and clears it on save', async ({ page, examParticipation, examNavigation }) => {
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);

        const sidebar = page.locator('jhi-exam-navigation-sidebar');
        // The exam has one exercise, so the sidebar has exactly one status indicator; its data-status is one of synced, synced saved and notSynced.
        const status = sidebar.getByTestId('sidebar-exercise-status');
        const saveButton = page.locator('#save-exam').first();
        const editor = page.locator('#text-editor').first();
        await editor.waitFor({ state: 'visible', timeout: 30000 });

        // Typing mutates isSynced in place. Nothing else here can trigger change detection, so both
        // indicators must react on their own. Each assertion resolves on the first poll that satisfies it,
        // well before the exam's 30s autosave could flip the flag back and hide a missing re-render.
        await editor.click();
        await editor.pressSequentially('Answer to the first exam question', { delay: 20 });
        await expect(status, 'the sidebar must warn about unsaved changes right after the student types').toHaveAttribute('data-status', 'notSynced', { timeout: 20000 });
        await expect(saveButton, 'the save button must become clickable once there are unsaved changes').toBeEnabled({ timeout: 20000 });

        // Saving flips isSynced and submitted back to true, again by in-place mutation.
        await saveButton.click();
        await expect(status, 'the sidebar must show the saved state after a successful save').toHaveAttribute('data-status', 'synced saved', { timeout: 20000 });
        await expect(saveButton, 'the save button must disable itself again once the submission is synced').toBeDisabled({ timeout: 20000 });

        // The regression proper: editing an already-saved submission must bring the warning back without
        // any unrelated interaction.
        await editor.click();
        await editor.pressSequentially(' and a correction', { delay: 20 });
        await expect(status, 'editing a saved submission must warn about unsaved changes again').toHaveAttribute('data-status', 'notSynced', { timeout: 20000 });
        await expect(saveButton, 'the save button must re-enable after the saved submission is edited').toBeEnabled({ timeout: 20000 });
    });
});

/**
 * The editor of a modeling exercise draws its own "All changes saved" overlay. It has to tell the student the same as the sidebar icon and
 * the save button, which the test above covers for a text exercise: all three are driven by `Submission.isSynced`, which the modeling page
 * mutates in place. The overlay once followed only the start of a save, so it kept claiming "All changes saved" for up to 30 s (until the
 * next autosave) while the sidebar already warned about unsaved changes. Apollon also reports selection changes as changes of the model, so a
 * plain click on the canvas must not mark a saved answer as unsaved either.
 * <p>
 * The test asserts the three indicators together after every step and relies on the retries of the assertions only, so an overlay that lags
 * behind fails after the expect timeout instead of passing because a later step happened to render it.
 */
test.describe('Exam unsaved changes indicator of a modeling exercise', { tag: '@slow' }, () => {
    let exam: any;
    let modelingExercise: any;

    test.beforeEach('Create an exam with a modeling exercise', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        modelingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING);
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('shows the same state in the overlay of the editor, the sidebar and the save button', async ({ page, examParticipation, examNavigation, modelingExerciseEditor }) => {
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(modelingExercise.exerciseGroup!.title!);
        await modelingExerciseEditor.waitForEditorReady(modelingExercise.id);

        // The pages the student has visited stay in the document, so everything is looked up inside the page of the exercise.
        const exercise = getExercise(page, modelingExercise.id);
        const overlay = exercise.getByTestId('modeling-editor-save-status');
        const sidebarStatus = page.locator('jhi-exam-navigation-sidebar').getByTestId('sidebar-exercise-status');
        const saveButton = exercise.getByTestId('exam-save-button');
        const submissionRequests = /\/modeling\/exercises\/\d+\/modeling-submissions/;
        const saveRequest = (status: number) =>
            page.waitForResponse((response) => response.request().method() === 'PUT' && submissionRequests.test(response.url()) && response.status() === status);

        const expectUnsaved = async (step: string) => {
            await expect(overlay, `${step}: the overlay of the editor warns about unsaved changes`).toHaveAttribute('data-state', 'unsaved');
            await expect(sidebarStatus, `${step}: the sidebar warns about unsaved changes`).toHaveAttribute('data-status', 'notSynced');
            await expect(saveButton, `${step}: the save button can be clicked`).toBeEnabled();
        };
        const expectSaved = async (step: string) => {
            await expect(overlay, `${step}: the overlay of the editor says all changes are saved`).toHaveAttribute('data-state', 'saved');
            await expect(sidebarStatus, `${step}: the sidebar shows the saved state`).toHaveAttribute('data-status', 'synced saved');
            await expect(saveButton, `${step}: the save button is disabled`).toBeDisabled();
        };
        const addClass = () => exercise.locator('[data-testid="apollon-palette"] button[aria-label="Add element: Class"]').click();
        // Resolves once the client has rendered what the last event changed: it renders on the next frame, so two frames and a task later nothing is pending.
        // An assertion that a state did not change would pass before the client had rendered the change without this.
        const afterRender = () => page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(resolve)))));
        // Holds the next save back until `release` is called, so that the state while the save is under way can be looked at. The save then fails with
        // the given status, or goes on to the server without one.
        const holdSave = async (failWith?: number) => {
            let release!: () => void;
            const released = new Promise<void>((resolve) => (release = resolve));
            await page.route(submissionRequests, async (route) => {
                if (route.request().method() !== 'PUT') {
                    return route.continue();
                }
                await released;
                return failWith ? route.fulfill({ status: failWith, contentType: 'application/json', body: '{}' }) : route.continue();
            });
            return release;
        };

        await test.step('Nothing was edited yet', async () => {
            await expect(overlay).toHaveAttribute('data-state', 'saved');
            await expect(sidebarStatus).toHaveAttribute('data-status', 'synced');
            await expect(saveButton).toBeDisabled();
        });

        await test.step('An edit shows at once in all three', async () => {
            await addClass();
            await expectUnsaved('after adding a class');
        });

        await test.step('A save that fails leaves all three unsaved', async () => {
            const release = await holdSave(500);
            const failedSave = saveRequest(500);
            await saveButton.click();
            await expect(overlay, 'while the save is under way the overlay says so').toHaveAttribute('data-state', 'saving');
            await expect(sidebarStatus, 'while the save is under way the sidebar still warns').toHaveAttribute('data-status', 'notSynced');
            release();
            await failedSave;
            await expectUnsaved('after a failed save');
        });

        await test.step('A save that works shows the saved state in all three', async () => {
            await page.unroute(submissionRequests);
            const release = await holdSave();
            const save = saveRequest(200);
            await saveButton.click();
            await expect(overlay, 'while the save is under way the overlay says so').toHaveAttribute('data-state', 'saving');
            await expect(sidebarStatus, 'while the save is under way the sidebar still warns').toHaveAttribute('data-status', 'notSynced');
            release();
            await save;
            await expectSaved('after saving');
            await page.unroute(submissionRequests);
        });

        await test.step('Selecting on the canvas is no edit', async () => {
            // Apollon reports a click on the canvas and on an element as a change of the model, although nothing changed: the answer stays saved.
            await page.evaluate((selector) => {
                const reports = { count: 0 };
                (window as any).__modelingModelReports = reports;
                (document.querySelector(selector) as any).__apollonEditor.subscribeToModelChange(() => reports.count++);
            }, `#exercise-${modelingExercise.id} jhi-modeling-editor`);
            // The added class sits in the middle of the canvas and is selected, so a click beside it deselects it.
            const canvas = exercise.locator('.react-flow__pane');
            const canvasBox = (await canvas.boundingBox())!;
            await canvas.click({ position: { x: canvasBox.width * 0.8, y: canvasBox.height * 0.5 } });
            await exercise.locator('.react-flow__node').first().click({ force: true });
            let reports = 0;
            await expect
                .poll(
                    async () => {
                        await afterRender();
                        const current = await page.evaluate(() => (window as any).__modelingModelReports.count);
                        const settled = current > 0 && current === reports;
                        reports = current;
                        return settled;
                    },
                    { message: 'Apollon reports the selection as changes of the model and then is still' },
                )
                .toBe(true);
            await expectSaved('after selecting on the canvas');
        });

        await test.step('An edit after a save shows again, and leaving the page saves it', async () => {
            await addClass();
            await expectUnsaved('after adding a second class');
            const save = saveRequest(200);
            await examNavigation.openOverview();
            await save;
            await examNavigation.openOrSaveExerciseByTitle(modelingExercise.exerciseGroup!.title!);
            await expectSaved('after leaving the page and coming back');
        });
    });
});
