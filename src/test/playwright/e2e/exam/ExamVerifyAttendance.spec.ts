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
 * verification page lists the students who started the exam and did not sign yet, and says that everybody passed when nobody is missing.
 */
test.describe('Exam attendance verification', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    /** A supervisor records that the student was checked and signed. */
    async function recordSignature(page: Page, student: UserCredentials) {
        const response = await page.request.post(`api/exam/courses/${course.id}/exams/${exam.id}/exam-users`, {
            multipart: {
                examUserDTO: {
                    name: 'examUserDTO',
                    mimeType: 'application/json',
                    buffer: Buffer.from(
                        JSON.stringify({ login: student.username, didCheckImage: true, didCheckName: true, didCheckLogin: true, didCheckRegistrationNumber: true }),
                    ),
                },
                file: { name: 'signature.png', mimeType: 'image/png', buffer: fs.readFileSync(signature) },
            },
        });
        expect(response.status(), `${student.username} signs`).toBe(200);
    }

    test('The page says that the exam has not started, lists the students who did not sign and confirms when everybody did', async ({
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
        exam = (await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, { course, students: [studentOne, studentTwo] })).exam;
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

        // A supervisor records the signature of one student: only the other one is left on the page.
        await login(tutor);
        await recordSignature(page, studentOne);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students/verify-attendance`);
        await expect(page.getByTestId('not-checked-count')).toHaveText('1');
        await expect(rows).toHaveCount(1);
        await expect(rows.first()).toHaveAttribute('data-login', studentTwo.username);

        // The other one signs too: everybody passed, and the list is gone.
        await login(tutor);
        await recordSignature(page, studentTwo);
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students/verify-attendance`);
        await expect(page.getByTestId('all-students-checked')).toContainText('All the students have been checked successfully');
        await expect(rows).toHaveCount(0);
    });
});
