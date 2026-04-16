import dayjs from 'dayjs';

import { Exam } from 'app/exam/shared/entities/exam.model';

import { dayjsToString, generateUUID, trimDate } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';
import { admin } from '../../../support/users';

// Common primitives
const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    workingTime: 5,
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Exam start text',
    endText: 'Exam end text',
    confirmationStartText: 'Exam confirmation start text',
    confirmationEndText: 'Exam confirmation end text',
};

const course = { id: SEED_COURSES.testExam.id } as any;

test.describe('Test Exam creation/deletion', { tag: '@fast' }, () => {
    let exam: Exam;

    test('Creates a test exam', async ({ login, page, examManagement, examCreation }) => {
        await login(admin);
        await page.goto(`/course-management/${course.id}/exams/new`);
        await examCreation.setTitle(examData.title);
        await examCreation.setTestMode();
        await examCreation.setVisibleDate(examData.visibleDate);
        await examCreation.setStartDate(examData.startDate);
        await examCreation.setEndDate(examData.endDate);
        await examCreation.setWorkingTime(examData.workingTime);
        await examCreation.setNumberOfExercises(examData.numberOfExercises);
        await examCreation.setExamMaxPoints(examData.maxPoints);

        await examCreation.setStartText(examData.startText);
        await examCreation.setEndText(examData.endText);
        await examCreation.setConfirmationStartText(examData.confirmationStartText);
        await examCreation.setConfirmationEndText(examData.confirmationEndText);

        const examResponse = await examCreation.submit();
        exam = { ...(await examResponse.json()), course };
        expect(examResponse.status()).toBe(201);
        expect(exam.title).toBe(examData.title);
        expect(exam.testExam).toBe(true);
        expect(trimDate(String(exam.visibleDate))).toBe(trimDate(dayjsToString(examData.visibleDate)));
        expect(trimDate(String(exam.startDate))).toBe(trimDate(dayjsToString(examData.startDate)));
        expect(trimDate(String(exam.endDate))).toBe(trimDate(dayjsToString(examData.endDate)));
        expect(exam.workingTime).toBe(examData.workingTime * 60);
        expect(exam.numberOfExercisesInExam).toBe(examData.numberOfExercises);
        expect(exam.examMaxPoints).toBe(examData.maxPoints);
        expect(exam.startText).toBe(examData.startText);
        expect(exam.endText).toBe(examData.endText);
        expect(exam.confirmationStartText).toBe(examData.confirmationStartText);
        expect(exam.confirmationEndText).toBe(examData.confirmationEndText);
        await page.waitForURL(`**/exams/${exam.id}**`);
        await expect(examManagement.getExamTitle().getByText(examData.title)).toBeVisible();
    });

    test.describe('Test exam deletion', () => {
        test.beforeEach(async ({ login, examAPIRequests }) => {
            await login(admin);
            examData.title = 'exam' + generateUUID();
            const examConfig = {
                course,
                title: examData.title,
                testExam: true,
            };
            exam = await examAPIRequests.createExam(examConfig);
        });

        test('Deletes an existing test exam', async ({ page, examDetails }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}`);
            await examDetails.deleteExam(examData.title);
        });
    });

    test.afterEach('Delete exam if exists', async ({ examAPIRequests }) => {
        if (exam?.id) {
            try {
                await examAPIRequests.deleteExam(exam);
            } catch {
                // Exam may already be deleted by the test
            }
        }
    });
});
