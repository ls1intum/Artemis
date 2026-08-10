import { expect } from '@playwright/test';

import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { test } from '../../../support/fixtures';
import { admin, tutor } from '../../../support/users';
import { Commands } from '../../../support/commands';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { newBrowserPage } from '../../../support/utils';
import { SEED_COURSES } from '../../../support/seedData';
import modelingExerciseSubmissionTemplate from '../../../fixtures/exercise/modeling/submission.json';

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

/**
 * The tutor training ("practice assessment") flow of an example submission: a tutor grades an example submission from
 * scratch and the server tells them how their assessment differs from the instructor's.
 *
 * Regressions this guards against:
 *  - "Submit assessment" was disabled on arrival, because the validity flag was only recomputed by a code path that
 *    never runs in this mode (no result is loaded here - the instructor's assessment is the solution).
 *  - The unreferenced ("additional") feedback editor was hidden entirely, because it was gated on a persisted result id.
 *  - A wrong assessment produced no verdict at all: the server put the multi-line correction-error JSON into an HTTP
 *    response header, which the container rejects, so the client received a body without the correction errors.
 */
test.describe('Modeling example submission practice assessment', { tag: '@slow' }, () => {
    let modelingExercise: ModelingExercise;
    let exampleSubmissionId: number;
    let practiceAssessmentUrl: string;

    test.beforeAll('Create an example submission used for tutorial with an instructor assessment', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        await Commands.login(page, admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });

        const exampleSubmissionResponse = await page.request.post(`api/assessment/exercises/${modelingExercise.id}/example-submissions`, {
            data: {
                exercise: modelingExercise,
                usedForTutorial: true,
                submission: { ...modelingExerciseSubmissionTemplate, id: null, participation: null, exampleSubmission: true },
            },
        });
        expect(exampleSubmissionResponse.ok()).toBe(true);
        exampleSubmissionId = (await exampleSubmissionResponse.json()).id;

        // A single unreferenced feedback keeps the expected solution independent of the diagram's element ids.
        const assessmentResponse = await page.request.put(`api/modeling/modeling-submissions/${exampleSubmissionId}/example-assessment`, {
            data: [{ credits: 5, text: 'Sample solution feedback', type: 'MANUAL_UNREFERENCED', reference: '1' }],
        });
        expect(assessmentResponse.ok()).toBe(true);

        practiceAssessmentUrl = `/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/example-submissions/${exampleSubmissionId}?toComplete=true`;
    });

    test('lets a tutor add additional feedback and submit the practice assessment', async ({ login, page, exerciseAssessment }) => {
        await login(tutor, `/course-management/${course.id}/assessment-dashboard/${modelingExercise.id}`);
        await exerciseAssessment.clickHaveReadInstructionsButton();

        await page.goto(practiceAssessmentUrl);

        const submit = page.locator('#submit-example-assessment');
        const unreferencedFeedback = page.locator('jhi-unreferenced-feedback');
        await expect(page.locator('jhi-modeling-assessment')).toBeVisible();

        // The tutor starts from an empty assessment and must be able to submit it right away.
        await expect(submit).toBeEnabled();
        // The instructor's assessment is the solution: it is never shown as the tutor's own, but the editor to write
        // one's own unreferenced feedback has to be there regardless.
        await expect(unreferencedFeedback).toBeVisible();
        await expect(unreferencedFeedback.locator('jhi-unreferenced-feedback-detail')).toHaveCount(0);

        await unreferencedFeedback.locator('.add-unreferenced-feedback').click();
        const feedbackCard = unreferencedFeedback.locator('jhi-unreferenced-feedback-detail').first();
        const score = feedbackCard.locator('input[type="number"]');
        await expect(feedbackCard).toBeVisible();

        // A feedback whose score has been cleared is not submittable ...
        await score.fill('');
        await expect(submit).toBeDisabled();

        // ... a score that does not match the instructor's is, it is just wrong.
        await score.fill('1');
        await feedbackCard.locator('textarea').fill('Not quite the instructor wording');
        await expect(submit).toBeEnabled();

        await submit.click();
        await expect(page.locator('.alert-inner').filter({ hasText: 'mistake' })).toBeVisible();
        await expect(feedbackCard).toContainText('score');

        // Correcting the score turns the same submission into a passing one.
        await score.fill('5');
        await feedbackCard.locator('textarea').fill('Sample solution feedback');
        await submit.click();
        await expect(page.locator('.alert-inner').filter({ hasText: 'good assessment' })).toBeVisible();
    });
});
