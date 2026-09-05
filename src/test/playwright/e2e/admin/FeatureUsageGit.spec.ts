import { Page, expect } from '@playwright/test';
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
        await login(admin);
        const before = await gitCallCounts(page);

        await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
        await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, cAllSuccessful, 'Feature usage git check');

        await login(admin);
        // Waits for the flush to land. At least one of each rather than exactly one, for the same reason the assertions
        // below use a lower bound: a parallel spec can raise these counters between the baseline and here.
        await expect
            .poll(
                async () => {
                    const now = await gitCallCounts(page);
                    return (
                        (now.get('fetch/assignment') ?? 0) - (before.get('fetch/assignment') ?? 0) >= 1 &&
                        (now.get('push/assignment') ?? 0) - (before.get('push/assignment') ?? 0) >= 1
                    );
                },
                { timeout: 90000, intervals: [5000] },
            )
            .toBeTruthy();

        const after = await gitCallCounts(page);
        // Asserted as an increase over a baseline taken in this test rather than as an absolute count. These rows are
        // process-wide and keyed only by feature, day and role, and the slow project runs fully parallel: other specs
        // clone and push assignment repositories against the same server and raise the very same counters. Requiring an
        // exact one would pass alone and fail intermittently in the suite. Once-per-operation semantics are pinned
        // deterministically in LocalVCUsageTrackingServiceTest instead, where a handshake request is rejected outright.
        expect((after.get('fetch/assignment') ?? 0) - (before.get('fetch/assignment') ?? 0)).toBeGreaterThanOrEqual(1);
        expect((after.get('push/assignment') ?? 0) - (before.get('push/assignment') ?? 0)).toBeGreaterThanOrEqual(1);

        // Parallel-safe and the claim that actually matters: the parsed segment is the repository type for staff
        // repositories but the *username* for student ones, so an unbounded identifier here would mean one feature per
        // student. No amount of concurrent traffic can add an identifier outside this set.
        const identifiers = [...after.keys()].sort();
        expect(identifiers.length).toBeGreaterThan(0);
        identifiers.forEach((identifier) => expect(identifier).toMatch(/^(fetch|push)\/(template|solution|tests|assignment|unknown)$/));
    });

    async function gitCallCounts(page: Page): Promise<Map<string, number>> {
        const response = await page.request.get('/api/admin/feature-usage?days=7');
        expect(response.ok()).toBeTruthy();
        const features = (await response.json()).features ?? [];
        return new Map(features.filter((feature: any) => feature.featureKind === 'GIT').map((feature: any) => [feature.identifier as string, (feature.callCount ?? 0) as number]));
    }
});
