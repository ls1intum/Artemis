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
 * This mode loads no result at all — the instructor's assessment is the solution the tutor is graded against — which
 * is what makes it worth an e2e. It pins three invariants that all depend on that: submitting is enabled on arrival,
 * the unreferenced feedback editor is available without a persisted result id, and a wrong assessment comes back with
 * the server's correction errors in the response body.
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

        await expect(submit).toBeEnabled();
        await expect(unreferencedFeedback).toBeVisible();
        await expect(unreferencedFeedback.locator('jhi-unreferenced-feedback-detail')).toHaveCount(0);

        await unreferencedFeedback.locator('.add-unreferenced-feedback').click();
        const feedbackCard = unreferencedFeedback.locator('jhi-unreferenced-feedback-detail').first();
        const score = feedbackCard.locator('input[type="number"]');
        await expect(feedbackCard).toBeVisible();

        await score.fill('');
        await expect(submit).toBeDisabled();

        await score.fill('1');
        await feedbackCard.locator('textarea').fill('Not quite the instructor wording');
        await expect(submit).toBeEnabled();

        await submit.click();
        await expect(page.locator('[data-testid="alert"]').filter({ hasText: 'mistake' })).toBeVisible();
        await expect(feedbackCard).toContainText('score');

        await score.fill('5');
        await feedbackCard.locator('textarea').fill('Sample solution feedback');
        await submit.click();
        await expect(page.locator('[data-testid="alert"]').filter({ hasText: 'good assessment' })).toBeVisible();
    });
});
