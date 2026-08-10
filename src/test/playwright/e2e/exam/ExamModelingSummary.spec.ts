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

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

/**
 * The exam summary renders the modeling submission inside a collapsible card, which
 * has no height of its own to hand down. The submission's layout assumed a sized
 * parent, so on this page the canvas resolved to zero and the student saw an empty
 * box where their diagram — and their feedback — should be.
 *
 * Nothing caught it: no existing exam coverage asserts that the summary's canvas is
 * on screen. This does, in the state that matters most, after an assessment exists.
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

        // Docked, not floating: reviewing is reading, and the summary has the width
        // to put the feedback beside the diagram rather than over it.
        await expect(panel).toHaveClass(/apollon-rail-disclosure--docked/);

        // And the diagram is framed clear of it - the camera has to refit once the
        // rail reserves its width, or the nodes stay hidden underneath.
        const panelBox = (await panel.boundingBox())!;
        const nodes = page.locator('jhi-modeling-exam-summary .react-flow__node');
        const nodeCount = await nodes.count();
        expect(nodeCount).toBeGreaterThan(0);
        for (let index = 0; index < nodeCount; index++) {
            const nodeBox = (await nodes.nth(index).boundingBox())!;
            expect(nodeBox.x + nodeBox.width, `node ${index} must not sit under the feedback panel`).toBeLessThanOrEqual(panelBox.x + 1);
        }
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });
});
