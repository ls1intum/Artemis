import * as fs from 'fs';
import * as os from 'os';
import path from 'path';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { ProjectType } from '../../support/constants';
import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import {
    DEFLATED,
    STORED,
    downloadArchive,
    expectEntry,
    expectUsableGitRepository,
    extractArchive,
    extractNestedArchive,
    readArchiveEntries,
} from '../../support/ArchiveInspector';

/*
 * A course archive is the one export that nests archives inside an archive: one per repository, next to the plain
 * CSV files that hold the scores. That mix is what the compression rules are about - deflating an already compressed
 * archive a second time buys almost nothing - and it is also the deepest test of the repository export itself,
 * because reaching a repository here means opening two archives and then handing the result to git.
 */

const EXECUTABLE_MODE = 0o755;

test.describe('Course archive', { tag: '@slow' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    test.beforeEach('Creates a finished course with a programming participation', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            projectType: ProjectType.GRADLE_GRADLE,
            releaseDate: dayjs().subtract(2, 'hours'),
            dueDate: dayjs().subtract(1, 'hour'),
        });

        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation: Participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaPartiallySuccessfulSubmission);

        // Archiving is refused while the course is still running, so it is only moved into the past once the
        // participation exists.
        await login(admin);
        await courseManagementAPIRequests.setCourseEndDate(course.id!);
    });

    test.afterEach('Deletes the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Archives a finished course and hands out a readable archive', async ({ page, login, courseManagementAPIRequests }) => {
        // Archiving exports every repository of the course, which outlasts the default budget for a slow test.
        test.setTimeout(300_000);

        // Archiving lives on the course settings page, not on the course management overview.
        await login(instructor, `/course-management/${course.id}/settings`);
        await page.locator('[data-testid="archiveButton"][data-mode="Course"]').click();
        await page.getByTestId('archive-confirm-button').click();

        await courseManagementAPIRequests.waitForCourseArchive(course.id!);

        await page.reload();
        const { filePath, suggestedFilename } = await downloadArchive(page, async () => {
            await page.locator('[data-testid="archive-download-button"][data-mode="Course"]').click();
        });

        expect(suggestedFilename).toMatch(/\.zip$/);
        const entries = await readArchiveEntries(filePath);
        expect(entries.length, 'the archive must not be empty').toBeGreaterThan(0);

        // Every repository of the exercise is nested as its own archive.
        const nestedArchives = entries.filter((entry) => entry.name.endsWith('.zip'));
        expect(nestedArchives.length, `the archive must nest one archive per repository, but holds:\n${entries.map((entry) => entry.name).join('\n')}`).toBeGreaterThan(0);

        // Deflating an already compressed archive a second time measured a fraction of what it costs, so those
        // entries are stored. Storing everything would be just as wrong, which the CSV assertion below covers.
        for (const nested of nestedArchives) {
            expect(nested.compressionMethod, `${nested.name} is already compressed and must be stored`).toBe(STORED);
            expect(nested.uncompressedSize, `${nested.name} must not be empty`).toBeGreaterThan(0);
        }

        // Storing everything would be just as wrong as deflating everything: the problem statements and exercise
        // details next to the repositories are plain text and still have to be compressed.
        const plainEntries = entries.filter((entry) => !entry.isDirectory && !entry.name.endsWith('.zip') && entry.uncompressedSize > 200);
        expect(
            plainEntries.map((entry) => entry.name),
            'the archive has to carry the plain files this assertion is about',
        ).not.toEqual([]);
        for (const plain of plainEntries) {
            expect(plain.compressionMethod, `${plain.name} is compressible and must still be deflated`).toBe(DEFLATED);
            expect(plain.compressedSize, `${plain.name} has to actually get smaller`).toBeLessThan(plain.uncompressedSize);
        }

        // Storing an entry must not change a byte of it: the nested archive still has to open, and the repository
        // inside it still has to be one git accepts, with its permissions intact.
        const solutionArchive = nestedArchives.find((entry) => /solution/i.test(entry.name));
        expect(solutionArchive, `the archive must nest the solution repository, but holds:\n${nestedArchives.map((entry) => entry.name).join('\n')}`).toBeDefined();

        const workingDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-course-archive-'));
        const nestedPath = await extractNestedArchive(filePath, solutionArchive!.name, workingDirectory);
        const nestedEntries = await readArchiveEntries(nestedPath);
        expectEntry(nestedEntries, '.git/HEAD');
        expect(expectEntry(nestedEntries, 'gradlew').unixMode, 'the executable bit has to survive both archives').toBe(EXECUTABLE_MODE);

        const extracted = path.join(workingDirectory, 'solution');
        await extractArchive(nestedPath, extracted);
        await expectUsableGitRepository(extracted);
    });
});
