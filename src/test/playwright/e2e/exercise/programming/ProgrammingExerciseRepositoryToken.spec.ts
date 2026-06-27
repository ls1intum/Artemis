import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { admin, instructor } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { GitCloneMethod } from '../../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

// Verifies that course staff get a repository-scoped VCS access token in the clone dialog of a base repository,
// instead of having to fall back to their account password. The TEMPLATE and SOLUTION staff repository views and the
// exercise detail page are exercised here through the UI; the TESTS and AUXILIARY repositories are covered end-to-end
// (real git clone / REST) by LocalVCFetchAndPushIntegrationTest and RepositoryVcsAccessTokenIntegrationTest.
test.describe('Programming exercise staff repository access token', { tag: '@fast' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
    });

    // The route segment of the staff repository view and the slug suffix the resulting clone URL must end with.
    const baseRepositories = [
        { repositoryType: 'TEMPLATE', slugSuffix: '-exercise.git' },
        { repositoryType: 'SOLUTION', slugSuffix: '-solution.git' },
    ];

    for (const { repositoryType, slugSuffix } of baseRepositories) {
        test(`Instructor obtains a repository-scoped token in the ${repositoryType} repository clone dialog`, async ({ login, programmingExerciseOverview }) => {
            // Open the staff repository view of the base repository directly.
            await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/repository/${repositoryType}`);

            await programmingExerciseOverview.openCloneMenu(GitCloneMethod.httpsWithToken);
            const cloneUrl = await programmingExerciseOverview.copyCloneUrl(GitCloneMethod.httpsWithToken);

            // The clone URL must embed a repository-scoped VCS access token (prefix "vcpat-") for the instructor and point at the requested base repository.
            expect(cloneUrl).toContain('vcpat-');
            expect(cloneUrl).toContain(instructor.username);
            expect(cloneUrl).toContain(slugSuffix);
        });
    }

    test('Instructor obtains a repository-scoped token on the exercise detail page without configuring a personal token', async ({ login, page, programmingExerciseOverview }) => {
        // Regression test: on the exercise detail page the clone dialog used to show a "set up your VCS token manually"
        // warning and a disabled copy button for staff who had no personal token. It must now provision a repository-scoped
        // token automatically, exactly like the dedicated repository view does.
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}`);

        // Scope to the template repository row (stable detail id) instead of relying on the order of code buttons on the page.
        const templateCodeButton = page.locator('[id="detail-value-artemisApp.programmingExercise.templateRepositoryUri"] .code-button');
        await programmingExerciseOverview.openCloneMenu(GitCloneMethod.httpsWithToken, templateCodeButton);
        const cloneUrl = await programmingExerciseOverview.copyCloneUrl(GitCloneMethod.httpsWithToken, templateCodeButton);

        expect(cloneUrl).toContain('vcpat-');
        expect(cloneUrl).toContain(instructor.username);
        expect(cloneUrl).toContain('-exercise.git');
    });
});
