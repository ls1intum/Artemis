import * as fs from 'fs';
import * as os from 'os';
import path from 'path';
import dayjs from 'dayjs';
import { Page, expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { ProjectType } from '../../support/constants';
import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import { downloadArchive, expectUsableGitRepository, extractArchive, readArchiveEntries } from '../../support/ArchiveInspector';

/*
 * The personal data export is the one export that has to hand back a directory per participation rather than an
 * archive per participation, because a student should not have to unpack a second archive to reach their own code.
 * It used to get there by cloning the repository and checking it out; it now reads the bare repository directly, so
 * this test exists to pin the layout and to prove the result is still a repository git accepts.
 */

const EXECUTABLE_MODE = 0o755;

/**
 * Deletes every data export the given user still has, so the table holds exactly the one the test creates.
 *
 * @param page  the page to issue the requests from, logged in as an administrator
 * @param login the user whose exports are removed
 */
async function removeExistingDataExports(page: Page, login: string) {
    const response = await page.request.get('api/admin/data-exports?page=0&size=200&sort=id,desc');
    expect(response.ok(), 'the existing data exports have to be readable to clean them up').toBe(true);
    for (const dataExport of (await response.json()) as { id: number; userLogin: string }[]) {
        if (dataExport.userLogin === login) {
            await page.request.delete(`api/admin/data-exports/${dataExport.id}`);
        }
    }
}

test.describe('Personal data export', { tag: '@slow' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    test.beforeEach('Creates a programming participation to export', async ({ page, login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            projectType: ProjectType.GRADLE_GRADLE,
            releaseDate: dayjs().subtract(1, 'hour'),
            dueDate: dayjs().add(1, 'day'),
        });

        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation: Participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaPartiallySuccessfulSubmission);

        // Earlier runs leave downloadable exports behind for the same student. The table only renders a download
        // button once an export is ready, so a leftover would be the one the test downloads while the new export is
        // still being created - and it belongs to a different course.
        await login(admin);
        await removeExistingDataExports(page, studentOne.username);
    });

    test.afterEach('Deletes the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Exports a repository as a walkable repository directory rather than a nested archive', async ({ page, login }) => {
        // Creating the export runs the whole export pipeline synchronously, which outlasts the default slow budget.
        test.setTimeout(300_000);

        await login(admin, '/admin/data-exports');
        await page.getByTestId('create-export-btn').click();

        await page.locator('#typeahead-search').fill(studentOne.username);
        await page.getByRole('option').filter({ hasText: studentOne.username }).first().click();
        await page.getByTestId('execute-now-radio').click();
        await page.getByTestId('submit-btn').click();

        const downloadButton = page.getByTestId('download-btn').first();
        await downloadButton.waitFor({ state: 'visible', timeout: 240_000 });
        const { filePath, suggestedFilename } = await downloadArchive(page, () => downloadButton.click());

        expect(suggestedFilename).toMatch(/\.zip$/);
        const entries = await readArchiveEntries(filePath);
        const names = entries.map((entry) => entry.name);

        // What the export owes the student regardless of what they participated in.
        expect(
            names.some((name) => name.endsWith('README.md')),
            'the export has to explain itself',
        ).toBe(true);
        expect(
            names.some((name) => name.includes('general_user_information')),
            'the export has to carry the account details',
        ).toBe(true);

        // The layout that must not change: a directory holding the repository, not an archive holding it.
        // The export covers every course the student ever took part in, and the shared test student accumulates those
        // across parallel workers, so the assertions have to name the course this test created.
        const headEntry = names.find((name) => name.includes(course.shortName!) && name.endsWith('/.git/HEAD'));
        expect(headEntry, `the export has to carry this course's participation as a repository directory, but holds:\n${names.join('\n')}`).toBeDefined();
        const repositoryPrefix = headEntry!.slice(0, headEntry!.length - '/.git/HEAD'.length);
        expect(
            names.filter((name) => name.startsWith(`${repositoryPrefix}/`) && name.endsWith('.zip')),
            'the repository must not be nested as a second archive',
        ).toEqual([]);

        const gradlew = entries.find((entry) => entry.name === `${repositoryPrefix}/gradlew`);
        expect(gradlew, 'the working tree has to be materialized next to the .git directory').toBeDefined();
        expect(gradlew!.unixMode, 'the executable bit has to survive the export').toBe(EXECUTABLE_MODE);

        // Reading the objects directly instead of cloning is only correct if the result is still usable.
        const extracted = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-data-export-'));
        await extractArchive(filePath, extracted);
        const commits = await expectUsableGitRepository(path.join(extracted, repositoryPrefix));
        expect(commits, "the student's own commits have to be in their data export").toBeGreaterThan(1);
    });
});
