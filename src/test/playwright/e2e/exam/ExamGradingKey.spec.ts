import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { asAdmin, newBrowserPage, prepareEndedExam, startAssessing } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { EXAM_DASHBOARD_TIMEOUT, RELOAD_RENDER_TIMEOUT } from '../../support/timeouts';
import { Page } from '@playwright/test';

const course = { id: SEED_COURSES.examResults.id } as any;

/** Grades from 5.0 (failed) to 1.0 (best), as a German grading scale: below 50 percent 5.0, from 50 percent 3.0, from 80 percent 1.0. */
const GRADE_STEPS = [
    { lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 50, upperBoundInclusive: false, gradeName: '5.0', isPassingGrade: false },
    { lowerBoundPercentage: 50, lowerBoundInclusive: true, upperBoundPercentage: 80, upperBoundInclusive: false, gradeName: '3.0', isPassingGrade: true },
    { lowerBoundPercentage: 80, lowerBoundInclusive: true, upperBoundPercentage: 100, upperBoundInclusive: true, gradeName: '1.0', isPassingGrade: true },
];

/** The bonus a student earns in the bonus exam: nothing below 50 percent, 0.3 from 50 percent, 0.7 from 80 percent. */
const BONUS_STEPS = [
    { lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 50, upperBoundInclusive: false, gradeName: '0.0', isPassingGrade: false },
    { lowerBoundPercentage: 50, lowerBoundInclusive: true, upperBoundPercentage: 80, upperBoundInclusive: false, gradeName: '0.3', isPassingGrade: true },
    { lowerBoundPercentage: 80, lowerBoundInclusive: true, upperBoundPercentage: 100, upperBoundInclusive: true, gradeName: '0.7', isPassingGrade: true },
];

/** Takes an exam with one text exercise, ends it and lets the tutor give the points; the results stay unpublished. */
async function prepareAssessedExam(page: Page, points: number): Promise<Exam> {
    const exam = await prepareEndedExam(course, ExerciseType.TEXT, page, 1, false);
    // A failed assessment must not leave the exam behind: the caller only learns of the exam when this returns.
    try {
        await Commands.login(page, tutor);
        await startAssessing(
            course.id!,
            exam.id!,
            EXAM_DASHBOARD_TIMEOUT,
            new ExamManagementPage(page),
            new CourseAssessmentDashboardPage(page),
            new ExerciseAssessmentDashboardPage(page),
        );
        const examAssessment = new ExamAssessmentPage(page);
        await examAssessment.addNewFeedback(points, 'Graded');
        expect((await examAssessment.submitTextAssessment()).status()).toBe(200);
        return exam;
    } catch (error) {
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        throw error;
    }
}

/**
 * The grading key turns the points of an exam into a grade, and a bonus exam improves that grade. The student of the exam gets 7 of 10 points
 * (grade 3.0) in the main exam and 9 of 10 points in the bonus exam (bonus 0.7), so the final grade is 2.3. Student and instructor must see the
 * same grades, and the student must see neither before the results are published.
 */
test.describe.serial('Exam grading key and bonus', { tag: '@slow' }, () => {
    test.describe.configure({ timeout: 300_000 });

    let mainExam: Exam;
    let bonusExam: Exam;

    test.beforeAll('Take, end and assess the main exam and the bonus exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        mainExam = await prepareAssessedExam(page, 7);
        bonusExam = await prepareAssessedExam(page, 9);

        await Commands.login(page, admin);
        const examAPIRequests = new ExamAPIRequests(page);
        await examAPIRequests.createGradingScale(mainExam, { gradeType: 'GRADE', gradeSteps: GRADE_STEPS });
        const bonusScaleId = await examAPIRequests.createGradingScale(bonusExam, { gradeType: 'BONUS', gradeSteps: BONUS_STEPS });
        await examAPIRequests.createBonus(mainExam, bonusScaleId, 'GRADES_CONTINUOUS', -1);
        await page.close();
    });

    test.afterAll('Delete the exams', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        const examAPIRequests = new ExamAPIRequests(page);
        await examAPIRequests.deleteExam(mainExam);
        await examAPIRequests.deleteExam(bonusExam);
        await page.close();
    });

    test('The student sees no grade before the results are published', async ({ page, login }) => {
        await login(studentOne, `/courses/${course.id}/exams/${mainExam.id}`);
        await expect(page.getByText('Your result will be published here as soon as the correction is finished.')).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        await expect(page.getByText('Grade before bonus')).toHaveCount(0);
        await expect(page.getByText('Grading Key')).toHaveCount(0);
    });

    test('The student sees the grade, the bonus, the final grade and the grading keys once the results are published', async ({ browser, page, login }) => {
        const publishedAt = await asAdmin(browser, async (adminExamRequests) => {
            await adminExamRequests.publishResultsIn(bonusExam, 5);
            return await adminExamRequests.publishResultsIn(mainExam, 5);
        });
        await login(studentOne, `/courses/${course.id}/exams/${mainExam.id}`);
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.waitUntilServerClockIsAfter(mainExam, publishedAt));
        await page.reload();

        // 7 of 10 points is 70 percent: grade 3.0 before the bonus, and the bonus of the exam with 90 percent (0.7) improves it to 2.3.
        await expect(page.getByRole('row', { name: /^Total 7 10 70 %$/ })).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        await expect(page.getByText(/Grade before bonus\s*3\.0/)).toBeVisible();
        await expect(page.getByText(/Grade after bonus\s*2\.3/)).toBeVisible();
        await expect(page.getByText(`You got a grade bonus of 0.7 from ${bonusExam.title}`)).toBeVisible();

        // The grading key and the bonus grading key list every step.
        await page.getByRole('button', { name: 'Grading Key', exact: true }).click();
        await page.getByRole('button', { name: 'Bonus Grading Key', exact: true }).click();
        for (const gradeName of ['5.0', '3.0', '1.0', '0.0', '0.3', '0.7']) {
            await expect(page.getByRole('cell', { name: gradeName, exact: true }).first(), `${gradeName} is in a grading key`).toBeVisible();
        }

        // The server hands out the same numbers.
        const studentExamId = await new ExamAPIRequests(page).getOwnStudentExamId(mainExam);
        const gradeSummary = await page.request.get(`api/exam/courses/${course.id}/exams/${mainExam.id}/student-exams/${studentExamId}/grade-summary`);
        expect(gradeSummary.status()).toBe(200);
        const studentResult = (await gradeSummary.json()).studentResult;
        expect(studentResult.overallPointsAchieved).toBe(7);
        expect(studentResult.overallGrade).toBe('3.0');
        expect(studentResult.hasPassed).toBe(true);
        expect(studentResult.gradeWithBonus.bonusGrade).toBe('0.7');
        expect(studentResult.gradeWithBonus.finalGrade).toBe('2.3');
    });

    test('The instructor sees grade, bonus and final grade of the student in the scores of the main exam', async ({ page, login }) => {
        await login(instructor, `/course-management/${course.id}/exams/${mainExam.id}/scores`);
        const row = page.locator(`[data-testid="student-result-row"][data-login="${studentOne.username}"]`);
        await expect(row).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        await expect(row.getByTestId('overall-points')).toContainText('7');
        await expect(row.getByTestId('overall-grade')).toContainText('3.0');
        await expect(row).toContainText('0.7');
        await expect(row).toContainText('2.3');
    });
});
