import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { ProgrammingLanguage } from '../../support/constants';
import { SEED_COURSES } from '../../support/seedData';
import { GitExerciseParticipation } from '../../support/pageobjects/exercises/programming/GitExerciseParticipation';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import cAllSuccessful from '../../fixtures/exercise/programming/c/all_successful/submission.json';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

/**
 * End-to-end coverage of the one part of the feature usage analysis that no unit test can reach: git.
 *
 * <p>
 * Git operations are counted in `LocalVCFetchFilter` and `LocalVCPushFilter`, which only run for a real request to the
 * embedded git server. Everything about that path is therefore untested outside this spec: whether the filters are wired
 * in at all, whether only the data-transfer POST is counted rather than all three requests of a clone, whether the
 * repository is reduced to a bounded kind instead of becoming one feature per student, and whether the row reaches the
 * database at all. A `GIT` feature is also the only kind created lazily on first sighting, so this is the only exercise
 * of that path end to end.
 *
 * Deliberately no build assertion. The counters are written by the git filters, so waiting for a build would add several
 * minutes and a dependency on the build agent to a test that does not need either.
 */
test.describe('Feature usage records git operations', { tag: '@slow' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create a programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
    });

    test('Counts a clone and a push of an assignment repository', async ({ page, login, programmingExerciseOverview }) => {
        test.slow();
        await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
        await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, cAllSuccessful, 'Feature usage git check');

        await login(admin);
        await expect
            .poll(
                async () => {
                    const response = await page.request.get('/api/admin/feature-usage?days=7');
                    expect(response.ok()).toBeTruthy();
                    const overview = await response.json();
                    return (overview.features ?? []).filter((feature: any) => feature.featureKind === 'GIT' && (feature.callCount ?? 0) > 0);
                },
                { timeout: 90000, intervals: [5000] },
            )
            .toHaveLength(2);

        const response = await page.request.get('/api/admin/feature-usage?days=7');
        const recorded = ((await response.json()).features ?? []).filter((feature: any) => feature.featureKind === 'GIT');

        // A student repository must collapse into one bounded identifier: the parsed segment is the username, so passing
        // it through would make every student their own feature and reproduce the cardinality problem this exists to avoid.
        expect(recorded.map((feature: any) => feature.identifier).sort()).toEqual(['fetch/assignment', 'push/assignment']);
        expect(recorded.every((feature: any) => feature.module === 'localvc')).toBeTruthy();
        // One count per operation, not one per HTTP request: a clone or push is three requests and only the transfer counts.
        expect(recorded.every((feature: any) => feature.callCount === 1)).toBeTruthy();
    });
});
