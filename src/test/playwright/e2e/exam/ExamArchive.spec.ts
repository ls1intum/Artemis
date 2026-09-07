import * as fs from 'fs';
import * as os from 'os';
import path from 'path';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';

import { Exam } from 'app/exam/shared/entities/exam.model';

import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { ExerciseType, ProgrammingLanguage } from '../../support/constants';
import { SEED_COURSES } from '../../support/seedData';
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
 * An exam archive is the record an institution keeps of an exam, and it nests one archive per repository the way a
 * course archive does. It is the second place where the compression rules and the repository export meet.
 *
 * The exam deliberately carries no student participation: the exercise repositories are what this test is about, and
 * driving a student through the exam editor would make it an exam participation test that happens to archive.
 */

const course = { id: SEED_COURSES.examManagement.id } as any;

test.describe('Exam archive', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Deletes the exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Archives a finished exam and hands out a readable archive', async ({ page, login, examAPIRequests, examExerciseGroupCreation }) => {
        // Exporting every repository of the exam outlasts the default budget for a slow test by far.
        test.setTimeout(600_000);

        await login(admin);
        const endDate = dayjs().add(15, 'seconds');
        exam = await examAPIRequests.createExam({ course, startDate: dayjs().subtract(1, 'minute'), endDate, gracePeriod: 0 });
        await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { programmingLanguage: ProgrammingLanguage.C });

        // Archiving is refused while the exam is still running.
        const remaining = endDate.diff(dayjs());
        if (remaining > 0) {
            await page.waitForTimeout(remaining + 2000);
        }

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}`);
        await page.locator('[data-testid="archiveButton"][data-mode="Exam"]').click();
        await page.getByTestId('archive-confirm-button').click();

        // Archiving runs asynchronously; the download button only appears once the archive is on disk.
        const downloadButton = page.locator('[data-testid="archive-download-button"][data-mode="Exam"]');
        await expect(async () => {
            await page.reload();
            await expect(downloadButton).toBeVisible({ timeout: 5000 });
        }).toPass({ timeout: 300_000 });

        const { filePath, suggestedFilename } = await downloadArchive(page, () => downloadButton.click());

        expect(suggestedFilename).toMatch(/\.zip$/);
        const entries = await readArchiveEntries(filePath);
        expect(entries.length, 'the archive must not be empty').toBeGreaterThan(0);

        const nestedArchives = entries.filter((entry) => entry.name.endsWith('.zip'));
        expect(
            nestedArchives.map((entry) => entry.name),
            `the archive must nest one archive per repository, but holds:\n${entries.map((entry) => entry.name).join('\n')}`,
        ).not.toEqual([]);
        for (const nested of nestedArchives) {
            expect(nested.compressionMethod, `${nested.name} is already compressed and must be stored`).toBe(STORED);
            expect(nested.uncompressedSize, `${nested.name} must not be empty`).toBeGreaterThan(0);
        }

        const plainEntries = entries.filter((entry) => !entry.isDirectory && !entry.name.endsWith('.zip') && entry.uncompressedSize > 200);
        expect(
            plainEntries.map((entry) => entry.name),
            'the archive has to carry the plain files this assertion is about',
        ).not.toEqual([]);
        for (const plain of plainEntries) {
            expect(plain.compressionMethod, `${plain.name} is compressible and must still be deflated`).toBe(DEFLATED);
        }

        // Storing an entry must not change a byte of it, which only a repository git accepts can prove.
        const solutionArchive = nestedArchives.find((entry) => /solution/i.test(entry.name));
        expect(solutionArchive, `the archive must nest the solution repository, but holds:\n${nestedArchives.map((entry) => entry.name).join('\n')}`).toBeDefined();

        const workingDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-exam-archive-'));
        const nestedPath = await extractNestedArchive(filePath, solutionArchive!.name, workingDirectory);
        expectEntry(await readArchiveEntries(nestedPath), '.git/HEAD');

        const extracted = path.join(workingDirectory, 'solution');
        await extractArchive(nestedPath, extracted);
        await expectUsableGitRepository(extracted);
    });
});
