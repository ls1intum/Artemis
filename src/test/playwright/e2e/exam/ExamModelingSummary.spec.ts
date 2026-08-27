import { expect } from '@playwright/test';
import dayjs, { Dayjs } from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { admin, instructor, studentOne, studentOneName, tutor } from '../../support/users';
import { test } from '../../support/fixtures';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { newBrowserPage, prepareExam, startAssessing, waitForExamEnd } from '../../support/utils';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';

/** Box geometry is rounded per element, so an edge flush against another overshoots it by a fraction of a pixel. */
const SUB_PIXEL_TOLERANCE = 1;

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

/**
 * The exam summary renders the modeling submission inside a collapsible card, which hands down no
 * height of its own — the one place the submission layout cannot assume a sized parent. Asserts the
 * student sees their diagram and their feedback in the state that matters, once an assessment exists.
 */
test.describe.serial('Exam modeling summary', { tag: '@slow' }, () => {
    let exam: Exam;
    let examEnd: Dayjs;

    test.beforeAll('Prepare and submit an exam', async ({ browser }) => {
        examEnd = dayjs().add(25, 'seconds');
        const page = await newBrowserPage(browser);
        exam = await prepareExam(course, examEnd, ExerciseType.MODELING, page);
        await page.close();
    });

    test('shows the assessed diagram and its feedback in the results', async ({
        page,
        login,
        examManagement,
        modelingExerciseAssessment,
        examAssessment,
        courseAssessment,
        exerciseAssessment,
    }) => {
        await login(instructor);
        await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
        await waitForExamEnd(exam, page);

        await login(tutor);
        await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
        await modelingExerciseAssessment.addNewFeedback(5, 'Good');
        await modelingExerciseAssessment.openAssessmentForComponent(0);
        await modelingExerciseAssessment.assessComponent(-1, 'Wrong');
        await modelingExerciseAssessment.clickNextAssessment();
        await modelingExerciseAssessment.assessComponent(0, 'Neutral');
        await modelingExerciseAssessment.clickNextAssessment();
        expect((await examAssessment.submitModelingAssessment()).status()).toBe(200);

        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);

        // The diagram itself, not a collapsed placeholder.
        const canvas = page.locator('jhi-modeling-exam-summary .apollon-editor');
        await expect(canvas).toBeVisible();
        await expect(page.locator('jhi-modeling-exam-summary .react-flow__node').first()).toBeVisible();
        const canvasBox = (await canvas.boundingBox())!;
        expect(canvasBox.height, 'the summary canvas must not collapse').toBeGreaterThan(200);
        expect(canvasBox.width).toBeGreaterThan(200);

        // The feedback the student came here to read, in the editor's own chrome.
        const panel = page.locator('jhi-modeling-exam-summary .apollon-rail-disclosure');
        await expect(panel).toBeVisible();
        await expect(panel.locator('.feedback-row').first()).toBeVisible();
        await expect(panel).toContainText('Good');

        // The panel floats over the canvas, so reserving rail width is not enough: the camera has to refit once the
        // rail settles, or the nodes keep their old framing and sit underneath. That refit is what is polled for.
        const nodes = page.locator('jhi-modeling-exam-summary .react-flow__node');
        expect(await nodes.count()).toBeGreaterThan(0);
        await expect
            .poll(
                async () => {
                    const panelBox = await panel.locator('.apollon-rail-disclosure__panel').boundingBox();
                    const nodeBoxes = await nodes.all().then((all) => Promise.all(all.map((node) => node.boundingBox())));
                    if (!panelBox || nodeBoxes.some((box) => !box)) {
                        return false;
                    }
                    return nodeBoxes.every((box) => box!.x + box!.width <= panelBox.x + SUB_PIXEL_TOLERANCE);
                },
                { message: 'every node must be framed clear of the feedback panel' },
            )
            .toBe(true);
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });
});
