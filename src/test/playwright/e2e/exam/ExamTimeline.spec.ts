import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { asAdmin, prepareRunningTextExam } from '../../support/utils';
import dayjs from 'dayjs';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * The exam timeline lets an instructor replay what a student wrote: every save of the student is a version with a timestamp, and moving along the
 * timeline shows the answer as it was at that moment.
 */
test.describe('Exam timeline', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The instructor replays the versions of an answer along the timeline', async ({ browser, page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        const prepared = await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, { course });
        exam = prepared.exam;

        // The student saves three versions of the answer, the last one handed in.
        await login(studentOne);
        const versions = ['First version of the answer', 'Second version of the answer', 'Third version of the answer'];
        for (const [index, text] of versions.entries()) {
            const saved = await page.request.post(`api/text/exercises/${prepared.exercise.id}/text-submissions`, {
                data: { text, submitted: index === versions.length - 1, language: 'ENGLISH' },
            });
            expect(saved.status()).toBe(200);
            // The timeline shows timestamps to the second, so the next version is saved in a later second.
            await asAdmin(browser, (adminRequests) => adminRequests.waitUntilServerClockIsAfter(exam, dayjs(saved.headers()['date'])));
        }
        const studentExamId = await examAPIRequests.getOwnStudentExamId(exam);

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/exam-timeline`);
        const answer = page.locator('#text-editor');
        // The timeline opens at the first version, and every version has a timestamp of its own.
        const timestamp = page.getByText('Selected Timestamp:');
        await expect(timestamp).toBeVisible();
        await expect(answer).toHaveValue(versions[0]);
        const timestamps = [await timestamp.innerText()];

        // Moving forward along the timeline shows the later versions ...
        const slider = page.getByRole('slider');
        await slider.focus();
        for (const version of versions.slice(1)) {
            await page.keyboard.press('ArrowRight');
            await expect(answer).toHaveValue(version);
            timestamps.push(await timestamp.innerText());
        }
        expect(new Set(timestamps).size, `each version has its own timestamp: ${timestamps.join(' | ')}`).toBe(versions.length);

        // ... and moving back shows the earlier ones again.
        await page.keyboard.press('ArrowLeft');
        await expect(answer).toHaveValue(versions[1]);
        await expect(timestamp).toHaveText(timestamps[1]);
    });
});
