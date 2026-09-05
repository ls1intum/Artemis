import dayjs from 'dayjs';
import { expect } from '@playwright/test';

import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { test } from '../../../support/fixtures';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { Commands } from '../../../support/commands';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { newBrowserPage } from '../../../support/utils';
import { SEED_COURSES } from '../../../support/seedData';
import { dismissPasskeyReminderIfPresent } from '../../../support/dismissPasskeyReminder';

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

test.describe('Modeling assessment workspace', { tag: '@fast' }, () => {
    test.use({ viewport: { width: 1440, height: 1000 } });

    let modelingExercise: ModelingExercise;

    test.beforeAll('Create a modeling exercise with a submission ready to assess', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        await Commands.login(page, admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        await Commands.login(page, studentOne);
        const participation = await (await exerciseAPIRequests.startExerciseParticipation(modelingExercise.id!)).json();
        await exerciseAPIRequests.makeModelingExerciseSubmission(modelingExercise.id!, participation, {
            explanationText: 'I modelled the domain around a single aggregate root.',
        });
        await Commands.login(page, instructor);
        await exerciseAPIRequests.updateModelingExerciseDueDate(modelingExercise, dayjs());
    });

    test('keeps the model, instructions, feedback, and private notes visible without page scrolling', async ({ login, page, exerciseAssessment }) => {
        await login(tutor, `/course-management/${course.id}/assessment-dashboard/${modelingExercise.id!}`);
        await dismissPasskeyReminderIfPresent(page);
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();

        const workspace = page.locator('jhi-assessment-workspace');
        const assessment = workspace.locator('jhi-modeling-assessment');
        const instructions = workspace.getByRole('heading', { name: 'Instructions', exact: true });
        const feedback = workspace.getByRole('heading', { name: 'Feedback', exact: true });
        const tutorNotes = workspace.getByRole('heading', { name: 'Private tutor notes', exact: true });
        await expect(assessment).toBeVisible();
        await expect(instructions).toBeVisible();
        await expect(feedback).toBeVisible();
        await expect(tutorNotes).toBeVisible();
        await expect(assessment.locator('[data-apollon-region="bottom-center"] jhi-modeling-explanation-editor')).toBeVisible();

        const horizontalSplitter = workspace.locator('.assessment-workspace__primary-split > [role="separator"]');
        const verticalSplitter = workspace.locator('.assessment-workspace__support-split > [role="separator"]');
        const instructionsSection = workspace.locator('.assessment-workspace__section').first();
        const assessmentWidthBeforeDrag = (await assessment.boundingBox())!.width;
        const horizontalSplitterBox = await horizontalSplitter.boundingBox();
        expect(horizontalSplitterBox).not.toBeNull();
        await page.mouse.move(horizontalSplitterBox!.x + horizontalSplitterBox!.width / 2, horizontalSplitterBox!.y + horizontalSplitterBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(horizontalSplitterBox!.x - 60, horizontalSplitterBox!.y + horizontalSplitterBox!.height / 2, { steps: 8 });
        await page.mouse.up();
        await expect.poll(async () => Math.abs((await assessment.boundingBox())!.width - assessmentWidthBeforeDrag)).toBeGreaterThan(40);

        const instructionsHeightBeforeDrag = (await instructionsSection.boundingBox())!.height;
        const verticalSplitterBox = await verticalSplitter.boundingBox();
        expect(verticalSplitterBox).not.toBeNull();
        await page.mouse.move(verticalSplitterBox!.x + verticalSplitterBox!.width / 2, verticalSplitterBox!.y + verticalSplitterBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(verticalSplitterBox!.x + verticalSplitterBox!.width / 2, verticalSplitterBox!.y - 50, { steps: 8 });
        await page.mouse.up();
        await expect.poll(async () => Math.abs((await instructionsSection.boundingBox())!.height - instructionsHeightBeforeDrag)).toBeGreaterThan(30);

        const [workspaceBox, assessmentBox, instructionsBox, feedbackBox] = await Promise.all([
            workspace.boundingBox(),
            assessment.boundingBox(),
            instructions.locator('..').locator('..').boundingBox(),
            feedback.locator('..').locator('..').boundingBox(),
        ]);
        expect(workspaceBox).not.toBeNull();
        expect(assessmentBox).not.toBeNull();
        expect(instructionsBox).not.toBeNull();
        expect(feedbackBox).not.toBeNull();
        expect(instructionsBox!.x).toBeGreaterThanOrEqual(assessmentBox!.x + assessmentBox!.width);
        expect(feedbackBox!.x).toBeGreaterThanOrEqual(assessmentBox!.x + assessmentBox!.width);
        expect(instructionsBox!.y + instructionsBox!.height).toBeLessThanOrEqual(feedbackBox!.y);
        expect(workspaceBox!.y + workspaceBox!.height).toBeLessThanOrEqual(1000);
        expect(await page.evaluate(() => document.scrollingElement!.scrollHeight <= window.innerHeight + 2)).toBe(true);

        await page.setViewportSize({ width: 1440, height: 760 });
        await expect
            .poll(async () => {
                const box = await workspace.boundingBox();
                return box ? Math.ceil(box.y + box.height) : Number.POSITIVE_INFINITY;
            })
            .toBeLessThanOrEqual(760);
        await expect.poll(() => page.evaluate(() => document.scrollingElement!.scrollHeight <= window.innerHeight + 2)).toBe(true);

        const apollonCanvas = assessment.locator('.apollon-editor');
        const bottomControls = [
            assessment.locator('[data-apollon-region="bottom-center"] .modeling-explanation-surface__surface'),
            assessment.locator('[data-apollon-control="apollon:zoom"]'),
            assessment.locator('[data-apollon-control="apollon:minimap"]'),
        ];
        const canvasBox = await apollonCanvas.boundingBox();
        const controlBoxes = await Promise.all(bottomControls.map((control) => control.boundingBox()));
        const footerBox = await page.locator('jhi-footer').boundingBox();
        expect(canvasBox).not.toBeNull();
        expect(footerBox).not.toBeNull();
        expect(
            controlBoxes.every((box) => !!box && box.y >= canvasBox!.y && box.y + box.height <= canvasBox!.y + canvasBox!.height && box.y + box.height <= footerBox!.y),
            JSON.stringify({ canvasBox, controlBoxes, footerBox }),
        ).toBe(true);

        const unreferencedFeedback = workspace.locator('.unreferenced-feedback');
        await expect(unreferencedFeedback.locator('tum-ui-message')).toBeVisible();
        await expect(unreferencedFeedback.locator('jhi-unreferenced-feedback-detail')).toHaveCount(0);
    });
});
