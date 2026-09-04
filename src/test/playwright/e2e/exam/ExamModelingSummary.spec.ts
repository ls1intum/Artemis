import { expect } from '@playwright/test';
import dayjs, { Dayjs } from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { test } from '../../support/fixtures';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { newBrowserPage, prepareExam, startAssessing, waitForExamEnd } from '../../support/utils';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';

const SUB_PIXEL_TOLERANCE = 1;

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

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
        await examManagement.verifySubmitted(course.id!, exam.id!, studentOne.displayName!);
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

        const canvas = page.locator('jhi-modeling-exam-summary .apollon-editor');
        await expect(canvas).toBeVisible();
        await expect(page.locator('jhi-modeling-exam-summary .react-flow__node').first()).toBeVisible();
        const canvasBox = (await canvas.boundingBox())!;
        expect(canvasBox.height, 'the summary canvas must not collapse').toBeGreaterThan(200);
        expect(canvasBox.width).toBeGreaterThan(200);

        const panel = page.locator('jhi-modeling-exam-summary .apollon-rail-disclosure');
        await expect(panel).toBeVisible();
        await expect(panel.locator('.feedback-row').first()).toBeVisible();
        await expect(panel).toContainText('Good');

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
