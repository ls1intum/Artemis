import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Fixtures } from '../../fixtures/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { UserCredentials } from '../../support/users';
import { Page } from '@playwright/test';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/** Two students hand in the same text in an exam: the plagiarism check finds the pair, a confirmed pair becomes one case per student, and a verdict is recorded. */
test.describe('Exam plagiarism', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    async function handInText(page: Page, exercise: Exercise, text: string) {
        const response = await page.request.post(`api/text/exercises/${exercise.id}/text-submissions`, { data: { text, submitted: true, language: 'ENGLISH' } });
        expect(response.status(), `hand in the text of exercise ${exercise.id}: ${await response.text()}`).toBe(200);
    }

    test('The check finds identical texts, confirming the pair creates the cases and the verdict is stored', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        const text = (await Fixtures.get('loremIpsum.txt'))!;

        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        for (const student of [studentOne, studentTwo] as UserCredentials[]) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        // Both students hand in the same text.
        for (const student of [studentOne, studentTwo]) {
            await login(student);
            await handInText(page, exercise, text);
        }

        // The check reports the pair with a similarity of one hundred percent.
        await login(instructor);
        const check = await page.request.get(`api/text/text-exercises/${exercise.id}/check-plagiarism?similarityThreshold=50&minimumScore=0&minimumSize=5`, { timeout: 120_000 });
        expect(check.status(), await check.text()).toBe(200);
        const comparisons = (await check.json()).plagiarismResult.comparisons;
        expect(comparisons, 'the two identical texts are one comparison').toHaveLength(1);
        expect(comparisons[0].similarity).toBeGreaterThanOrEqual(90);
        expect(comparisons[0].status, 'a new comparison has no decision yet').toBe('NONE');

        // Before a decision there is no case.
        const casesBefore = await page.request.get(`api/plagiarism/courses/${course.id}/exams/${exam.id}/plagiarism-cases/for-instructor`);
        expect(casesBefore.status()).toBe(200);
        expect(await casesBefore.json()).toHaveLength(0);

        // Confirming the pair creates a case for each of the two students.
        const confirm = await page.request.put(`api/plagiarism/courses/${course.id}/plagiarism-comparisons/${comparisons[0].id}/status`, { data: { status: 'CONFIRMED' } });
        expect(confirm.status()).toBe(200);
        const cases = await (await page.request.get(`api/plagiarism/courses/${course.id}/exams/${exam.id}/plagiarism-cases/for-instructor`)).json();
        expect(cases).toHaveLength(2);
        expect(cases.map((plagiarismCase: { student: { login: string } }) => plagiarismCase.student.login).sort()).toEqual([studentOne.username, studentTwo.username].sort());

        // The instructor finds both cases on the page of the exam.
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/plagiarism-cases`);
        for (const student of [studentOne, studentTwo]) {
            await expect(page.getByRole('link', { name: new RegExp(`\\(${student.username}\\)`) })).toBeVisible();
        }

        // A warning keeps its message and a point deduction its points, and each verdict records who gave it.
        const verdicts = [
            { plagiarismCase: cases[0], verdict: 'WARNING', verdictMessage: 'Please cite your sources', verdictPointDeduction: 0 },
            { plagiarismCase: cases[1], verdict: 'POINT_DEDUCTION', verdictMessage: '', verdictPointDeduction: 3 },
        ];
        for (const { plagiarismCase, ...verdict } of verdicts) {
            const saved = await page.request.put(`api/plagiarism/courses/${course.id}/plagiarism-cases/${plagiarismCase.id}/verdict`, { data: verdict });
            expect(saved.status()).toBe(200);
            const stored = await (await page.request.get(`api/plagiarism/courses/${course.id}/plagiarism-cases/${plagiarismCase.id}/for-instructor`)).json();
            expect(stored.verdict).toBe(verdict.verdict);
            expect(stored.verdictBy.login).toBe(instructor.username);
            expect(stored.verdictDate).toBeTruthy();
            if (verdict.verdict === 'WARNING') {
                expect(stored.verdictMessage).toBe(verdict.verdictMessage);
            } else {
                expect(stored.verdictPointDeduction).toBe(verdict.verdictPointDeduction);
            }
        }

        // A student is only told about a case once the instructor has notified them with a post.
        for (const student of [studentOne, studentTwo]) {
            await login(student);
            const own = await page.request.get(`api/plagiarism/courses/${course.id}/exercises/${exercise.id}/plagiarism-case`);
            expect(own.status()).toBe(200);
            expect((await own.text()).trim(), `${student.username} has not been notified`).toBe('');
        }
    });
});
