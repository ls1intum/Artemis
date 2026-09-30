import * as fs from 'fs';
import path from 'path';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';

import { Exam } from 'app/exam/shared/entities/exam.model';

import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { ExerciseType, ProgrammingLanguage } from '../../support/constants';
import { SEED_COURSES } from '../../support/seedData';
import { downloadArchive, readArchiveEntries } from '../../support/ArchiveInspector';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * What an institution keeps of an exam that students took: the archive nests the repository of every student next to the repositories of the
 * exercise. Cleaning the exam up afterwards deletes the repositories of the students from the server, while the archive stays downloadable.
 */
test.describe('Exam archive and cleanup with a student', { tag: '@slow' }, () => {
    let exam: Exam;
    let downloadDirectories: string[] = [];

    test.afterEach('Deletes the exam and the downloaded archives', async ({ login, examAPIRequests }) => {
        for (const directory of downloadDirectories) {
            fs.rmSync(directory, { recursive: true, force: true });
        }
        downloadDirectories = [];
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The archive holds the repository of the student, and the cleanup deletes it but keeps the archive', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examExerciseGroupCreation,
    }) => {
        // Archiving every repository of the exam outlasts the default budget for a slow test by far.
        test.setTimeout(600_000);

        await login(admin);
        exam = await examAPIRequests.createExam({ course, startDate: dayjs().subtract(1, 'minute'), endDate: dayjs().add(30, 'minutes'), gracePeriod: 0 });
        const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { programmingLanguage: ProgrammingLanguage.C });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        // Preparing the exercises creates the repository of the student.
        await examAPIRequests.prepareExerciseStartForExam(exam);
        const before = await exerciseAPIRequests.getProgrammingExerciseParticipation(exercise.id!);
        expect(before.repositoryUri, 'the student has a repository').toBeTruthy();

        // The exam is over, so it can be archived.
        await examAPIRequests.finishExam(exam);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}`);
        await page.locator('[data-testid="archiveButton"][data-mode="Exam"]').click();
        await page.getByTestId('archive-confirm-button').click();
        const downloadButton = page.locator('[data-testid="archive-download-button"][data-mode="Exam"]');
        await expect(async () => {
            await page.reload();
            await expect(downloadButton).toBeVisible({ timeout: 5000 });
        }).toPass({ timeout: 300_000 });

        // The archive nests the repository of the student.
        const { filePath } = await downloadArchive(page, () => downloadButton.click());
        downloadDirectories.push(path.dirname(filePath));
        const nestedNames = (await readArchiveEntries(filePath)).filter((entry) => entry.name.endsWith('.zip')).map((entry) => entry.name);
        expect(
            nestedNames.some((name) => name.toLowerCase().includes(studentOne.username.toLowerCase())),
            `the archive must nest the repository of ${studentOne.username}, but holds:\n${nestedNames.join('\n')}`,
        ).toBe(true);
        expect(
            nestedNames.some((name) => /solution/i.test(name)),
            'the archive nests the solution repository as well',
        ).toBe(true);

        // Cleaning up asks for the name of the exam, deletes the repositories of the students and leaves the archive alone.
        await page.getByRole('button', { name: 'Cleanup' }).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText('This will delete all student programming exercise repositories in the exam.');
        await dialog.locator('#confirm-entity-name').fill(exam.title!);
        const cleanedUp = page.waitForResponse((response) => response.url().endsWith(`/exams/${exam.id}/cleanup`) && response.request().method() === 'DELETE');
        await dialog.getByTestId('delete-dialog-confirm-button').click();
        expect((await cleanedUp).status()).toBe(200);

        await login(admin);
        const after = await exerciseAPIRequests.getProgrammingExerciseParticipation(exercise.id!);
        expect(after.repositoryUri ?? '', 'the repository of the student is gone').toBe('');
        expect((await examAPIRequests.getExam(exam)).examArchivePath, 'the archive is still recorded').toBeTruthy();
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}`);
        await expect(page.locator('[data-testid="archive-download-button"][data-mode="Exam"]')).toBeVisible();
        const again = await downloadArchive(page, () => page.locator('[data-testid="archive-download-button"][data-mode="Exam"]').click());
        downloadDirectories.push(path.dirname(again.filePath));
        expect(fs.statSync(again.filePath).size, 'the archive can still be downloaded and is not empty').toBeGreaterThan(0);
    });
});
