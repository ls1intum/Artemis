import * as fs from 'fs';
import * as os from 'os';
import path from 'path';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

import { test } from '../../../support/fixtures';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { ProjectType } from '../../../support/constants';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import {
    ArchiveEntry,
    DEFLATED,
    downloadArchive,
    expectEntry,
    expectSingleEntryEndingWith,
    expectUsableGitRepository,
    extractArchive,
    extractNestedArchive,
    readArchiveEntries,
} from '../../../support/ArchiveInspector';

/*
 * Covers every way a programming exercise repository leaves Artemis. Each test looks inside the archive rather than
 * only checking that a download happened, because the defects these exports have had - a lost executable bit, a
 * working tree that does not match the index it ships with, a .git directory that should not be there - are all
 * invisible from the outside.
 *
 * The exercise is created as a Gradle project on purpose: its repositories carry an executable `gradlew`, which is
 * the file whose permissions the export has to preserve for an extracted repository to be clean.
 */

const EXECUTABLE_MODE = 0o755;
const READ_WRITE_MODE = 0o644;

function temporaryDirectory(prefix: string): string {
    return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

/** The names of the top-level directories of an archive, i.e. one per exported participation. */
function topLevelDirectories(entries: ArchiveEntry[]): string[] {
    return entries.filter((entry) => entry.isDirectory && entry.name.split('/').filter(Boolean).length === 1).map((entry) => entry.name.replace('/', ''));
}

test.describe('Programming exercise repository export', { tag: '@slow' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let participation: Participation;
    let auxiliaryRepositoryId: number;

    test.beforeEach('Creates a Gradle exercise with a student participation', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            projectType: ProjectType.GRADLE_GRADLE,
            releaseDate: dayjs().subtract(1, 'hour'),
            dueDate: dayjs().add(1, 'day'),
            auxiliaryRepositories: [{ name: 'helper', checkoutDirectory: 'helper', description: 'A helper repository' }],
        });
        auxiliaryRepositoryId = exercise.auxiliaryRepositories![0].id!;
        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaPartiallySuccessfulSubmission);
    });

    test.afterEach('Deletes the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    /**
     * The export an instructor gets when every rewriting checkbox is unticked. This is the path that reads the bare
     * repository directly instead of cloning it, so the archive has to be indistinguishable from what a checkout
     * produced: a directory per participation whose working tree, index and history agree.
     */
    test('Exports selected participations with their history', async ({ page, login, programmingExerciseExportDialog }) => {
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/scores`);
        await programmingExerciseExportDialog.open();
        await programmingExerciseExportDialog.disableAllRewritingOptions();
        // Opened from the scores page nothing is preselected, so the export covers everyone who participated.
        await programmingExerciseExportDialog.setOption('allStudents', true);

        const { filePath, suggestedFilename } = await downloadArchive(page, () => programmingExerciseExportDialog.export());

        expect(suggestedFilename, 'the archive is named after the course and the exercise').toContain(course.shortName);
        expect(suggestedFilename).toMatch(/\.zip$/);

        const entries = await readArchiveEntries(filePath);
        const participationDirectories = topLevelDirectories(entries);
        expect(participationDirectories, 'exactly one directory per exported participation').toHaveLength(1);
        const repositoryDirectory = participationDirectories[0];
        expect(repositoryDirectory, 'the directory identifies the participant').toContain(studentOne.username);

        // A .git directory alone proves nothing; these are the files that make it a repository git can read.
        expectEntry(entries, `${repositoryDirectory}/.git/HEAD`);
        expectEntry(entries, `${repositoryDirectory}/.git/config`);
        expectSingleEntryEndingWith(entries, '.pack');
        expectSingleEntryEndingWith(entries, '.idx');

        // The defect this guards: java.util.zip cannot record permissions, so gradlew extracted as non-executable and
        // git - which tracks the executable bit - reported a modification before anyone had touched the working tree.
        expect(expectEntry(entries, `${repositoryDirectory}/gradlew`).unixMode, 'gradlew must stay executable').toBe(EXECUTABLE_MODE);
        expect(expectEntry(entries, `${repositoryDirectory}/build.gradle`).unixMode, 'a regular file must not become executable').toBe(READ_WRITE_MODE);

        const extracted = temporaryDirectory('artemis-export-history-');
        await extractArchive(filePath, extracted);
        const repositoryPath = path.join(extracted, repositoryDirectory);
        const commits = await expectUsableGitRepository(repositoryPath);
        expect(commits, 'the history has to carry the template commit and the student submission').toBeGreaterThan(1);

        // The archive must not disclose the server-internal path of the bare repository it was read from.
        const config = fs.readFileSync(path.join(repositoryPath, '.git', 'config'), 'utf8');
        expect(config, 'the generated config must not carry a remote').not.toContain('[remote');
        expect(config, 'file mode tracking has to stay on, otherwise the executable bit is meaningless').toContain('filemode = true');
    });

    /**
     * The same dialog with the options an instructor sees by default. Those rewrite the repository, so this export
     * still goes through a checkout, and the result has to keep the layout and the rewriting the options promise.
     */
    test('Exports by participant login with the default rewriting options', async ({ page, login, programmingExerciseExportDialog }) => {
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/scores`);
        await programmingExerciseExportDialog.open();
        await programmingExerciseExportDialog.setParticipantIdentifiers(studentOne.username);
        await programmingExerciseExportDialog.setOption('addParticipantName', true);
        await programmingExerciseExportDialog.setOption('combineStudentCommits', true);
        await programmingExerciseExportDialog.setOption('anonymizeRepository', false);

        const { filePath } = await downloadArchive(page, () => programmingExerciseExportDialog.export());

        const entries = await readArchiveEntries(filePath);
        const participationDirectories = topLevelDirectories(entries);
        expect(participationDirectories, 'the rewriting export keeps producing a directory per participation').toHaveLength(1);
        expect(participationDirectories[0]).toContain(studentOne.username);

        expectEntry(entries, `${participationDirectories[0]}/.git/HEAD`);
        expect(expectEntry(entries, `${participationDirectories[0]}/gradlew`).unixMode, 'a rewritten repository keeps its permissions too').toBe(EXECUTABLE_MODE);

        const extracted = temporaryDirectory('artemis-export-rewritten-');
        await extractArchive(filePath, extracted);
        await expectUsableGitRepository(path.join(extracted, participationDirectories[0]));
    });

    /**
     * The dialog can be opened from the scores page with nothing preselected. Exporting then used to send an empty
     * participant list, which produced a request URL with an empty path segment that the server answered with 404.
     */
    test('Refuses to export when nothing is selected', async ({ page, login, programmingExerciseExportDialog }) => {
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/scores`);
        await programmingExerciseExportDialog.open();
        await programmingExerciseExportDialog.setOption('allStudents', false);
        await programmingExerciseExportDialog.setParticipantIdentifiers('  ,  , ');

        const exportRequests: string[] = [];
        page.on('request', (request) => {
            if (request.url().includes('export-repos-by')) {
                exportRequests.push(request.url());
            }
        });

        await programmingExerciseExportDialog.export();

        await expect(page.locator('[data-testid="alert"][data-alert-type="danger"] .message'), 'the dialog has to say what is missing').toContainText(
            /select at least one participant/i,
        );
        expect(exportRequests, 'nothing may be requested from the server').toEqual([]);
        await expect(programmingExerciseExportDialog.dialog(), 'the dialog stays open so the selection can be corrected').toBeVisible();
    });

    /**
     * What a student gets for their own participation: the code they submitted, deliberately without the .git
     * directory, so the download carries no history.
     */
    test('Lets a student download their own repository without the history', async ({ page, login }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}/repository/${participation.id}`);
        const { filePath, suggestedFilename } = await downloadArchive(page, async () => {
            await page.locator('jhi-programming-exercise-student-repo-download button').first().click();
        });

        expect(suggestedFilename).toMatch(/\.zip$/);
        const entries = await readArchiveEntries(filePath);
        expect(
            entries.filter((entry) => entry.name.startsWith('.git/')),
            'a student download must not carry the git history',
        ).toEqual([]);
        expectEntry(entries, 'build.gradle');
        expect(expectEntry(entries, 'gradlew').unixMode, 'the snapshot keeps the executable bit as well').toBe(EXECUTABLE_MODE);
        expect(
            entries.some((entry) => entry.name.endsWith('BubbleSort.java')),
            'the submitted sources have to be in the snapshot',
        ).toBe(true);
    });

    /**
     * The same repository as seen by a tutor, who reaches it through the participation rather than through their own
     * exercise page.
     */
    test('Lets a tutor download a single student repository', async ({ page, login }) => {
        await login(tutor, `/course-management/${course.id}/programming-exercises/${exercise.id}/repository/USER/${participation.id}`);
        const { filePath } = await downloadArchive(page, async () => {
            await page.locator('jhi-programming-exercise-student-repo-download button').first().click();
        });

        const entries = await readArchiveEntries(filePath);
        expect(entries.length, 'the archive has to hold the working tree').toBeGreaterThan(1);
        expect(
            entries.filter((entry) => entry.name.startsWith('.git/')),
            'a single repository download carries no history',
        ).toEqual([]);
        expect(expectEntry(entries, 'gradlew').unixMode).toBe(EXECUTABLE_MODE);
    });

    /**
     * The exercise's own repositories, which instructors download to inspect or to hand on. These keep their history,
     * so the extracted directory has to be a repository git accepts.
     */
    // The route segment and the repository slug differ for the template repository, which git serves as "-exercise".
    for (const { repositoryType, slug } of [
        { repositoryType: 'TEMPLATE', slug: 'exercise' },
        { repositoryType: 'SOLUTION', slug: 'solution' },
        { repositoryType: 'TESTS', slug: 'tests' },
    ]) {
        test(`Downloads the ${repositoryType.toLowerCase()} repository with its history`, async ({ page, login }) => {
            await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/repository/${repositoryType}`);
            const { filePath, suggestedFilename } = await downloadArchive(page, async () => {
                await page.locator('jhi-programming-exercise-instructor-repo-download button').first().click();
            });

            expect(suggestedFilename.toLowerCase(), 'the file is named after the repository it holds').toContain(slug);
            const entries = await readArchiveEntries(filePath);
            expectEntry(entries, '.git/HEAD');
            expectEntry(entries, '.git/config');
            expectSingleEntryEndingWith(entries, '.pack');

            const extracted = temporaryDirectory(`artemis-export-${repositoryType.toLowerCase()}-`);
            await extractArchive(filePath, extracted);
            await expectUsableGitRepository(extracted);
        });
    }

    /**
     * An auxiliary repository is exported the same way, through a route that also carries the repository id.
     */
    test('Downloads an auxiliary repository with its history', async ({ page, login }) => {
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/repository/AUXILIARY/${auxiliaryRepositoryId}`);
        const { filePath } = await downloadArchive(page, async () => {
            await page.locator('jhi-programming-exercise-instructor-repo-download button').first().click();
        });

        const entries = await readArchiveEntries(filePath);
        expectEntry(entries, '.git/HEAD');
        const extracted = temporaryDirectory('artemis-export-auxiliary-');
        await extractArchive(filePath, extracted);
        await expectUsableGitRepository(extracted);
    });

    /**
     * The exercise material export, which is what the exercise import reads back. It nests one archive per repository
     * plus the problem statement and the exercise details, and the nested archives are stored rather than deflated a
     * second time.
     */
    test('Exports the exercise material for re-import', async ({ page, login }) => {
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}`);
        const { filePath } = await downloadArchive(page, async () => {
            await page.locator('jhi-programming-exercise-instructor-exercise-download button').first().click();
        });

        const entries = await readArchiveEntries(filePath);
        const names = entries.map((entry) => entry.name);
        expect(
            names.some((name) => name.endsWith('.json')),
            'the exercise details have to be part of the material export',
        ).toBe(true);
        // The three parts the import reads back: the repositories, the problem statement and the exercise settings.
        expect(
            names.some((name) => name.startsWith('Problem-Statement-') && name.endsWith('.md')),
            `the problem statement is missing from:\n${names.join('\n')}`,
        ).toBe(true);
        expect(
            names.some((name) => name.startsWith('Exercise-Details-') && name.endsWith('.json')),
            `the exercise details are missing from:\n${names.join('\n')}`,
        ).toBe(true);

        const repositoryBundles = entries.filter((entry) => entry.name.endsWith('.zip'));
        expect(
            repositoryBundles.map((entry) => entry.name),
            'the repositories are bundled into a single nested archive',
        ).toHaveLength(1);
        expect(repositoryBundles[0].uncompressedSize, 'the bundle must not be empty').toBeGreaterThan(0);

        // Opening the bundle is what proves the nesting is intact rather than merely present.
        const workingDirectory = temporaryDirectory('artemis-material-export-');
        const bundlePath = await extractNestedArchive(filePath, repositoryBundles[0].name, workingDirectory);
        const bundled = (await readArchiveEntries(bundlePath)).map((entry) => entry.name);
        for (const slug of ['exercise', 'solution', 'tests']) {
            expect(
                bundled.some((name) => name.includes(slug)),
                `the bundle must carry the ${slug} repository, but holds:\n${bundled.join('\n')}`,
            ).toBe(true);
        }
    });

    /**
     * Deflating an archive that only holds compressed archives spends CPU on the whole payload for almost nothing, so
     * those entries are stored while ordinary files keep being deflated. Both halves are asserted, because storing
     * everything would be just as wrong as deflating everything.
     */
    test('Stores nested archives but still deflates plain files', async ({ page, login }) => {
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}`);
        const { filePath } = await downloadArchive(page, async () => {
            await page.locator('jhi-programming-exercise-instructor-exercise-download button').first().click();
        });

        const entries = await readArchiveEntries(filePath);
        const compressible = entries.filter((entry) => !entry.isDirectory && (entry.name.endsWith('.json') || entry.name.endsWith('.md')) && entry.uncompressedSize > 200);
        expect(compressible.length, 'the fixture only tests anything if the archive holds a compressible file').toBeGreaterThan(0);
        for (const entry of compressible) {
            expect(entry.compressionMethod, `${entry.name} is compressible and must still be deflated`).toBe(DEFLATED);
            expect(entry.compressedSize, `${entry.name} has to actually get smaller`).toBeLessThan(entry.uncompressedSize);
        }
    });
});

/**
 * The example solution download is the one repository export a student may trigger for something that is not theirs,
 * and only once the example solution publication date has passed.
 */
test.describe('Programming exercise example solution export', { tag: '@slow' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    test.beforeEach('Creates an exercise whose example solution is published', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            projectType: ProjectType.GRADLE_GRADLE,
            releaseDate: dayjs().subtract(2, 'hours'),
            dueDate: dayjs().subtract(1, 'hour'),
            exampleSolutionPublicationDate: dayjs().subtract(30, 'minutes'),
        });
    });

    test.afterEach('Deletes the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Lets a student download the published example solution', async ({ page, login }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        // The example solution lives in a panel that starts collapsed, so its download button is not reachable yet.
        await page.getByTestId('example-solution-toggle').first().click();
        const downloadButton = page.locator('jhi-programming-exercise-example-solution-repo-download button').first();
        await downloadButton.waitFor({ state: 'visible' });
        const { filePath, suggestedFilename } = await downloadArchive(page, () => downloadButton.click());

        expect(suggestedFilename.toLowerCase()).toContain('solution');
        const entries = await readArchiveEntries(filePath);
        expect(
            entries.filter((entry) => entry.name.startsWith('.git/')),
            'the example solution is handed out as code, not as a repository',
        ).toEqual([]);
        expect(expectEntry(entries, 'gradlew').unixMode).toBe(EXECUTABLE_MODE);
        expect(
            entries.some((entry) => entry.name.endsWith('BubbleSort.java')),
            'the solution sources have to be in the archive',
        ).toBe(true);
    });
});
