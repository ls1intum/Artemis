import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examAssessment.id } as any;

const GRADE_STEPS = [
    { lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 50, upperBoundInclusive: false, gradeName: '5.0', isPassingGrade: false },
    { lowerBoundPercentage: 50, lowerBoundInclusive: true, upperBoundPercentage: 80, upperBoundInclusive: false, gradeName: '3.0', isPassingGrade: true },
    { lowerBoundPercentage: 80, lowerBoundInclusive: true, upperBoundPercentage: 100, upperBoundInclusive: true, gradeName: '1.0', isPassingGrade: true },
];
const BONUS_STEPS = [
    { lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 50, upperBoundInclusive: false, gradeName: '0.0', isPassingGrade: false },
    { lowerBoundPercentage: 50, lowerBoundInclusive: true, upperBoundPercentage: 80, upperBoundInclusive: false, gradeName: '0.3', isPassingGrade: true },
    { lowerBoundPercentage: 80, lowerBoundInclusive: true, upperBoundPercentage: 100, upperBoundInclusive: true, gradeName: '0.7', isPassingGrade: true },
];

/**
 * The instructor of an exam decides in the bonus page which exam gives a bonus, how it is calculated, and whether it is added or subtracted. The
 * page previews the calculation, the choice is stored when saved, survives a reload, can be changed, and is gone after it was deleted.
 */
test.describe('Exam bonus management', { tag: '@slow' }, () => {
    let mainExam: Exam;
    let bonusExam: Exam;

    test.beforeEach('Create the exams with their grading keys', async ({ login, examAPIRequests }) => {
        await login(admin);
        const uid = generateUUID();
        mainExam = await examAPIRequests.createExam({ course, title: `Main ${uid}`, examMaxPoints: 10 });
        bonusExam = await examAPIRequests.createExam({ course, title: `Bonus source ${uid}`, examMaxPoints: 10 });
        await examAPIRequests.createGradingScale(mainExam, { gradeType: 'GRADE', gradeSteps: GRADE_STEPS });
        await examAPIRequests.createGradingScale(bonusExam, { gradeType: 'BONUS', gradeSteps: BONUS_STEPS });
    });

    test.afterEach('Delete the exams', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(mainExam);
        await examAPIRequests.deleteExam(bonusExam);
    });

    test('A bonus is created, previewed, stored, changed and deleted in the bonus page', async ({ page, login }) => {
        const bonusUrl = `api/assessment/courses/${course.id}/exams/${mainExam.id}/bonuses`;
        await login(instructor, `/course-management/${course.id}/exams/${mainExam.id}/bonus`);

        // Nothing is chosen yet, and the exam has no bonus.
        expect((await page.request.get(bonusUrl)).status()).toBe(404);
        await expect(page.getByRole('button', { name: 'Grades', exact: true })).toHaveAttribute('aria-pressed', 'false');

        // Each choice opens the next step: strategy, then calculation, then source.
        await page.getByRole('button', { name: 'Grades', exact: true }).click();
        await page.getByRole('button', { name: '−', exact: true }).click();
        await page.getByTitle('Select bonus source').selectOption({ label: bonusExam.title! });
        await expect(page.getByRole('cell', { name: '0.7', exact: true }).first()).toBeVisible();

        // The preview calculates the final grade from points that can be entered: 7 of 10 points is 3.0, 9 of 10 points in the source gives 0.7.
        const [examPoints, sourcePoints] = await page.getByRole('spinbutton').all();
        await examPoints.fill('7');
        await sourcePoints.fill('9');
        await sourcePoints.blur();
        await expect(page.getByRole('row').filter({ hasText: '3.0 − 0.7 = 2.3' }).last()).toBeVisible();

        // Saving stores the choice.
        const created = page.waitForResponse((response) => response.url().endsWith('/bonuses') && response.request().method() === 'POST');
        await page.getByRole('button', { name: 'Save' }).click();
        expect((await created).status()).toBe(201);
        const stored = await (await page.request.get(bonusUrl)).json();
        expect(stored.weight).toBe(-1);
        expect(stored.bonusStrategy).toBe('GRADES_CONTINUOUS');

        // After a reload the page shows what was stored.
        await page.reload();
        await expect(page.getByRole('button', { name: 'Grades', exact: true })).toHaveAttribute('aria-pressed', 'true');
        await expect(page.getByRole('button', { name: '−', exact: true })).toHaveAttribute('aria-pressed', 'true');
        await expect(page.getByRole('cell', { name: '0.7', exact: true }).first()).toBeVisible();

        // Changing the calculation to an addition is stored as well.
        await page.getByRole('button', { name: '+', exact: true }).click();
        const updated = page.waitForResponse((response) => response.url().includes('/bonuses/') && response.request().method() === 'PUT');
        await page.getByRole('button', { name: 'Save' }).click();
        expect((await updated).status()).toBe(200);
        expect((await (await page.request.get(bonusUrl)).json()).weight).toBe(1);

        // Deleting it takes the bonus away.
        await page.getByRole('button', { name: 'Delete' }).click();
        const deleted = page.waitForResponse((response) => response.url().includes('/bonuses/') && response.request().method() === 'DELETE');
        await page.getByRole('dialog').getByTestId('delete-dialog-confirm-button').click();
        expect((await deleted).status()).toBe(200);
        expect((await page.request.get(bonusUrl)).status()).toBe(404);
    });
});
