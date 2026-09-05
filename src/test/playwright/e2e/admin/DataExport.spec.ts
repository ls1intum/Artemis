import * as fs from 'fs';
import * as os from 'os';
import path from 'path';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

import { test } from '../../support/fixtures';
import { UserCredentials, UserRole, admin } from '../../support/users';
import { ProjectType } from '../../support/constants';
import { generateUUID } from '../../support/utils';
import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import { downloadArchive, expectUsableGitRepository, extractArchive, readArchiveEntries } from '../../support/ArchiveInspector';

/*
 * The personal data export is the one export that has to hand back a directory per participation rather than an
 * archive per participation, because a student should not have to unpack a second archive to reach their own code.
 * It used to get there by cloning the repository and checking it out; it now reads the bare repository directly, so
 * this test exists to pin the layout and to prove the result is still a repository git accepts.
 */

const EXECUTABLE_MODE = 0o755;

test.describe('Personal data export', { tag: '@slow' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let student: UserCredentials;

    test.beforeEach('Creates a programming participation to export', async ({ page, login, courseManagementAPIRequests, exerciseAPIRequests, userManagementAPIRequests }) => {
        await login(admin);

        // A student of this test's own, rather than one of the shared fixtures. A personal data export covers every
        // course its subject ever joined, and a shared student collects courses from every other spec in the run,
        // so the work this test asks for would grow with the suite until it outlasts any wait. Its own student has
        // exactly the one course below, whatever else is running.
        const studentLogin = `artemis_export_${generateUUID()}`;
        student = { username: studentLogin, password: studentLogin };
        const created = await userManagementAPIRequests.createUser(student.username, student.password, UserRole.Student);
        expect(created.ok(), 'the export needs a student of its own, so creating one has to succeed').toBe(true);

        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, student);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            projectType: ProjectType.GRADLE_GRADLE,
            releaseDate: dayjs().subtract(1, 'hour'),
            dueDate: dayjs().add(1, 'day'),
        });

        await login(student);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation: Participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaPartiallySuccessfulSubmission);

        await login(admin);
    });

    test.afterEach('Deletes the course and the student', async ({ login, courseManagementAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        try {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        } finally {
            // In a finally, so that a course deletion which exhausts its retries does not leave the account behind, and
            // asserted, so that a cleanup which silently stops working shows up as a failure rather than as accounts
            // accumulating in the database.
            const response = await userManagementAPIRequests.deleteUser(student.username);
            expect(response.ok(), 'the student this test created has to be removed again').toBe(true);
        }
    });

    test('Exports a repository as a walkable repository directory rather than a nested archive', async ({ page, login }) => {
        // Creating the export runs the whole export pipeline synchronously, which outlasts the default slow budget.
        test.setTimeout(300_000);

        await login(admin, '/admin/data-exports');
        await page.getByTestId('create-export-btn').click();

        await page.locator('#typeahead-search').fill(student.username);
        await page.getByRole('option').filter({ hasText: student.username }).first().click();
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
