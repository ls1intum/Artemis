import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { newBrowserPage } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * The suspicious behavior analysis of an exam compares the sessions that were recorded while the students took it. Two students who take
 * the exam from the same browser on the same machine share an IP address and a browser fingerprint, which is exactly what the analysis
 * flags; the same students are not flagged by a criterion that they do not meet.
 */
test.describe('Exam suspicious behavior analysis', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Sessions of different students with the same IP address or browser fingerprint are flagged, others are not', async ({
        browser,
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        // Both students take the exam from this machine, so each of them has a session with this IP address and browser fingerprint.
        for (const student of [studentOne, studentTwo]) {
            const studentPage = await newBrowserPage(browser);
            await Commands.login(studentPage, student);
            await studentPage.goto(`/courses/${course.id}/exams/${exam.id}`);
            await new ExamStartEndPage(studentPage).startExam();
            await expect(studentPage.getByTestId('hand-in-early')).toBeVisible();
        }

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/suspicious-behavior`);
        const analyze = page.getByRole('button', { name: 'Analyze Sessions' });
        const criterion = (id: string) => page.locator(`#${id}`);

        // Nothing is analyzed until a criterion is chosen.
        await expect(analyze).toBeDisabled();

        // Same IP address in different students' exams: one case with both students.
        await criterion('different-student-exams-same-ip').check();
        await analyze.click();
        await expect(page.getByText(/This exam has\s*1\s*cases\./)).toBeVisible();
        await page.locator('#view-sessions-btn').click();
        await expect(page.getByText('Suspicious because of:')).toBeVisible();
        await expect(page.getByText('Different student exams with the same IP address')).toBeVisible();
        const rows = page.locator('table tbody tr');
        await expect(rows).toHaveCount(2);
        await expect(rows.filter({ hasText: studentOne.username })).toHaveCount(1);
        await expect(rows.filter({ hasText: studentTwo.username })).toHaveCount(1);

        // The same holds for the browser fingerprint.
        await page.goBack();
        await criterion('different-student-exams-same-ip').uncheck();
        await criterion('different-student-exams-same-fingerprints').check();
        await analyze.click();
        await expect(page.getByText(/This exam has\s*1\s*cases\./)).toBeVisible();
        await page.locator('#view-sessions-btn').click();
        await expect(page.getByText('Different student exams with the same browser fingerprint')).toBeVisible();
        await expect(page.locator('table tbody tr')).toHaveCount(2);
        await page.goBack();

        // Criteria the students do not meet flag nothing: each of them used one IP address and one browser, and both are inside the local range.
        await criterion('different-student-exams-same-fingerprints').uncheck();
        await criterion('same-student-exam-different-ips').check();
        await criterion('same-student-exam-different-fingerprints').check();
        await criterion('ip-outside-of-range').check();
        await page.locator('#ip-subnet').fill('127.0.0.0/8');
        await analyze.click();
        await expect(page.getByText(/This exam has\s*0\s*cases\./)).toBeVisible();
        await expect(page.locator('#view-sessions-btn')).toHaveCount(0);
    });
});
