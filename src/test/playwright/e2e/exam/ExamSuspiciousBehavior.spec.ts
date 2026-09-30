import { expect, Browser, Page } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { newBrowserPage, prepareRunningTextExam } from '../../support/utils';
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

    /** Browser contexts opened by the test, closed again however the test ends. */
    let openedPages: Page[] = [];
    async function openPage(browser: Browser): Promise<Page> {
        const page = await newBrowserPage(browser);
        openedPages.push(page);
        return page;
    }

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        for (const openedPage of openedPages) {
            await openedPage.context().close();
        }
        openedPages = [];
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
        exam = (await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, { course, students: [studentOne, studentTwo] })).exam;

        // Both students take the exam from this machine, so each of them has a session with this IP address and browser fingerprint.
        for (const student of [studentOne, studentTwo]) {
            const studentPage = await openPage(browser);
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
        const rows = page.getByTestId('suspicious-session-row');
        await expect(rows).toHaveCount(2);
        await expect(rows.filter({ hasText: studentOne.username })).toHaveCount(1);
        await expect(rows.filter({ hasText: studentTwo.username })).toHaveCount(1);
        // The address the server saw depends on where the test runs: directly it is the loopback (reported as ::1 or 127.0.0.1), behind the
        // load balancer of the multi-node topology it is an IPv4 address of that network. The range that contains it is built from what was seen.
        const observedIp = (await rows.first().getByTestId('suspicious-session-ip-address').innerText()).trim();
        expect(observedIp).toMatch(/^(\d+\.\d+\.\d+\.\d+|::1|0:0:0:0:0:0:0:1)$/);
        const rangeContainingIp = /^\d+\.\d+\.\d+\.\d+$/.test(observedIp) && !observedIp.startsWith('127.') ? `${observedIp}/32` : '127.0.0.0/8';

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

        // Criteria the students do not meet flag nothing: each of them used one IP address and one browser, and that address is inside the range.
        await criterion('different-student-exams-same-fingerprints').uncheck();
        await criterion('same-student-exam-different-ips').check();
        await criterion('same-student-exam-different-fingerprints').check();
        await criterion('ip-outside-of-range').check();
        await page.locator('#ip-subnet').fill(rangeContainingIp);
        await analyze.click();
        await expect(page.getByText(/This exam has\s*0\s*cases\./)).toBeVisible();
        await expect(page.locator('#view-sessions-btn')).toHaveCount(0);

        // A range that does not contain the address flags the sessions of both students. The range check compares IPv4 addresses only, so this
        // control needs an IPv4 address: behind the load balancer of the multi-node topology there is one, on a direct connection there is none.
        if (/^\d+\.\d+\.\d+\.\d+$/.test(observedIp)) {
            await page.locator('#ip-subnet').fill('203.0.113.0/24');
            await analyze.click();
            await expect(page.getByText(/This exam has\s*[1-9]\d*\s*cases\./)).toBeVisible();
            await page.locator('#view-sessions-btn').click();
            await expect(page.getByText('IP address outside of range')).toBeVisible();
            await expect(page.locator('table tbody tr').filter({ hasText: studentOne.username })).toHaveCount(1);
            await expect(page.locator('table tbody tr').filter({ hasText: studentTwo.username })).toHaveCount(1);
        }
    });
});
