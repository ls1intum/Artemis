import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { admin, instructor } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { GitCloneMethod } from '../../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

// Verifies that course staff get a repository-scoped VCS access token in the clone dialog of a base repository,
// instead of having to fall back to their account password.
test.describe('Programming exercise staff repository access token', { tag: '@fast' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
    });

    test('Instructor obtains a repository-scoped token in the template repository clone dialog', async ({ login, programmingExerciseOverview }) => {
        // Open the staff repository view of the template repository directly.
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/repository/TEMPLATE`);

        await programmingExerciseOverview.openCloneMenu(GitCloneMethod.httpsWithToken);
        const cloneUrl = await programmingExerciseOverview.copyCloneUrl(GitCloneMethod.httpsWithToken);

        // The clone URL must embed a repository-scoped VCS access token (prefix "vcpat-") for the instructor and point at the template repository.
        expect(cloneUrl).toContain('vcpat-');
        expect(cloneUrl).toContain(instructor.username);
        expect(cloneUrl).toContain('-exercise.git');
    });
});
