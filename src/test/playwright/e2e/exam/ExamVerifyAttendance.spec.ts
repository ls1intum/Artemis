import fs from 'fs';
import path from 'path';
import { expect, Page } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo, tutor, UserCredentials } from '../../support/users';
import { prepareRunningTextExam } from '../../support/utils';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;
const signature = path.resolve(__dirname, '../../../../test/resources/test-data/exam-users/examUserSigningImage.png');

/**
 * While an exam is running, the supervisors verify the attendance: they check the student, have them sign and record that in the exam. The
 * verification page retains registered students, distinguishes incomplete checks from signatures, and allows post-exam attendance corrections.
 */
test.describe('Exam attendance verification', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    /** A supervisor records that the student was checked and signed. */
    async function recordSignature(page: Page, student: UserCredentials, didCheckName = true) {
        const response = await page.request.post(`api/exam/courses/${course.id}/exams/${exam.id}/exam-users`, {
            multipart: {
                examUserDTO: {
                    name: 'examUserDTO',
                    mimeType: 'application/json',
                    buffer: Buffer.from(JSON.stringify({ login: student.username, didCheckImage: true, didCheckName, didCheckLogin: true, didCheckRegistrationNumber: true })),
                },
                file: { name: 'signature.png', mimeType: 'image/png', buffer: fs.readFileSync(signature) },
            },
        });
        expect(response.status(), `${student.username} signs`).toBe(200);
    }

    test('The page retains attendance records, identifies incomplete signed checks and supports corrections after the exam', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
    }) => {
        // Before the start there is nothing to verify.
        await login(admin);
        const upcoming = await examAPIRequests.createExam({ course, visibleDate: dayjs().subtract(1, 'day'), startDate: dayjs().add(2, 'days'), endDate: dayjs().add(3, 'days') });
        await login(instructor, `/course-management/${course.id}/exams/${upcoming.id}/students/verify-attendance`);
        await expect(page.getByTestId('exam-not-started')).toContainText('The exam has not started yet');
        await login(admin);
        await examAPIRequests.deleteExam(upcoming);

        // The exam is running and both students started it.
        exam = (await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, { course, students: [studentOne, studentTwo], examOptions: { gracePeriod: 0 } })).exam;
        for (const student of [studentOne, studentTwo]) {
            await login(student);
            await new ExamAPIRequests(page).getOwnStudentExamForConduction(exam);
        }

        // Nobody signed yet: the page lists both students, and shows that they started and did not hand in.
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students/verify-attendance`);
        const rows = page.getByTestId('attendance-check-row');
        await expect(page.getByTestId('not-checked-count')).toHaveText('2');
        await expect(rows).toHaveCount(2);
        for (const student of [studentOne, studentTwo]) {
            const row = rows.and(page.locator(`[data-login="${student.username}"]`));
            await expect(row).toContainText('Yes');
            await expect(row).toContainText('No');
        }

        const first = rows.and(page.locator(`[data-login="${studentOne.username}"]`));
        const second = rows.and(page.locator(`[data-login="${studentTwo.username}"]`));
        await expect(first.getByTestId('manual-attendance')).toBeDisabled();
        await expect(second.getByTestId('manual-attendance')).toBeDisabled();

        // Signed students remain available for review, alongside those with incomplete checks.
        await login(tutor);
        await recordSignature(page, studentOne);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students/verify-attendance`);
        await expect(page.getByTestId('not-checked-count')).toHaveText('1');
        await expect(rows).toHaveCount(2);
        await expect(first.getByTestId('attendance-check-status')).toHaveAttribute('data-checked', 'true');
        await expect(second.getByTestId('attendance-check-status')).toHaveAttribute('data-checked', 'false');

        // A signature alone does not complete the identity checks.
        await login(tutor);
        await recordSignature(page, studentTwo, false);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students/verify-attendance`);
        await expect(page.getByTestId('not-checked-count')).toHaveText('1');
        await expect(rows).toHaveCount(2);
        const firstSignature = first.getByRole('img', { name: `Signature of student ${studentOne.username}`, exact: true });
        const secondSignature = second.getByRole('img', { name: `Signature of student ${studentTwo.username}`, exact: true });
        await expect(secondSignature).toBeVisible();
        await expect(second.getByTestId('attendance-check-status')).toHaveAttribute('data-checked', 'false');
        const firstSignatureSource = await firstSignature.getAttribute('src');
        const secondSignatureSource = await secondSignature.getAttribute('src');

        await examAPIRequests.concludeExam(exam, { publishResults: false });
        await page.reload();
        await expect(second.getByTestId('manual-attendance')).toBeEnabled();

        const setAttendance = async (student: UserCredentials, value: string) => {
            const row = rows.and(page.locator(`[data-login="${student.username}"]`));
            const saved = page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().endsWith('/attendance'));
            await row.getByTestId('manual-attendance').selectOption(value);
            expect((await saved).status()).toBe(204);
            await expect(row.getByTestId('manual-attendance')).toBeEnabled();
            await expect(row.getByTestId('manual-attendance')).toHaveValue(value);
        };

        await setAttendance(studentTwo, 'true');
        await expect(page.getByTestId('all-students-checked')).toContainText('All the students have been checked successfully');
        await expect(rows).toHaveCount(2);
        await expect(secondSignature).toHaveAttribute('src', secondSignatureSource!);

        await setAttendance(studentOne, 'false');
        await expect(firstSignature).toHaveAttribute('src', firstSignatureSource!);
        await expect(first.getByTestId('attendance-check-status')).toHaveAttribute('data-checked', 'true');

        // Reset restores the original incomplete identity check without erasing the signature.
        await setAttendance(studentTwo, '');
        await expect(page.getByTestId('not-checked-count')).toHaveText('1');
        await expect(second.getByTestId('attendance-check-status')).toHaveAttribute('data-checked', 'false');
        await expect(secondSignature).toHaveAttribute('src', secondSignatureSource!);

        // A manual absence is also a completed decision, even with incomplete original checks.
        await setAttendance(studentTwo, 'false');
        await expect(page.getByTestId('all-students-checked')).toBeVisible();
        await expect(second.getByTestId('attendance-check-status')).toHaveAttribute('data-checked', 'true');
        await expect(rows).toHaveCount(2);
    });
});
