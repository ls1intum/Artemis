import path from 'path';
import { expect, Page } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, UserRole } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;
const imagesPdf = path.resolve(__dirname, '../../../../test/resources/test-data/exam-users/studentsWithImages.pdf');

/** The PDF holds one image for each of these registration numbers. The numbers are unique, so the students are accounts of this test. */
const students = [
    { login: 'e2e_exam_image_1', registrationNumber: '03756882' },
    { login: 'e2e_exam_image_2', registrationNumber: '03756883' },
    { login: 'e2e_exam_image_3', registrationNumber: '03756884' },
    { login: 'e2e_exam_image_4', registrationNumber: '03756885' },
];

/**
 * An instructor uploads a PDF with the pictures of the students: each picture belongs to the registered student with the registration number next
 * to it, and is shown in the list of students afterwards. Pictures of students that are not registered for the exam are counted as not found.
 */
test.describe.serial('Exam student images', { tag: '@slow' }, () => {
    let exam: Exam | undefined;

    test.beforeEach('Create the students with their registration numbers', async ({ login, userManagementAPIRequests }) => {
        await login(admin);
        for (const { login: studentLogin, registrationNumber } of students) {
            // A previous run that was interrupted may have left the account behind, and registration numbers are unique.
            if ((await userManagementAPIRequests.getUser(studentLogin)).ok()) {
                expect((await userManagementAPIRequests.deleteUser(studentLogin)).ok(), `delete the leftover account ${studentLogin}`).toBe(true);
            }
            const created = await userManagementAPIRequests.createUser(studentLogin, 'Test-password-1234', UserRole.Student, registrationNumber);
            expect(created.status(), `create ${studentLogin}: ${await created.text()}`).toBe(201);
        }
    });

    test.afterEach('Delete the exam and the students', async ({ login, examAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        if (exam) {
            await examAPIRequests.deleteExam(exam);
            exam = undefined;
        }
        for (const { login: studentLogin } of students) {
            expect((await userManagementAPIRequests.deleteUser(studentLogin)).ok(), `delete ${studentLogin}`).toBe(true);
        }
    });

    async function uploadImages(page: Page) {
        await page.getByRole('button', { name: 'Logistics' }).click();
        await page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Upload images' }).last().click();
        const dialog = page.getByRole('dialog');
        await dialog.locator('#importPDF').setInputFiles(imagesPdf);
        await expect(dialog.locator('#import')).toBeEnabled();
        const saved = page.waitForResponse((response) => response.url().includes('exam-users-save-images'), { timeout: 120_000 });
        await dialog.locator('#import').click();
        const response = await saved;
        expect(response.status()).toBe(200);
        return { dialog, result: (await response.json()) as { numberOfUsersNotFound: number; numberOfImagesSaved: number; listOfExamUserRegistrationNumbers: string[] } };
    }

    test('The pictures of the PDF are assigned to the registered students and shown in the list', async ({ page, login, examAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        for (const { login: studentLogin } of students) {
            await examAPIRequests.registerStudentForExam(exam, { username: studentLogin, password: '' });
        }

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        const images = page.getByTestId('student-image');
        await expect(images).toHaveCount(students.length);
        for (const { login: studentLogin } of students) {
            await expect(images.and(page.locator(`[data-login="${studentLogin}"]`)), `${studentLogin} has no picture yet`).toHaveAttribute('data-has-image', 'false');
        }

        const { dialog, result } = await uploadImages(page);
        expect(result.listOfExamUserRegistrationNumbers ?? [], 'no registration number is left without a student').toEqual([]);
        expect(result.numberOfUsersNotFound).toBe(0);
        expect(result.numberOfImagesSaved).toBe(students.length);
        await expect(dialog).toContainText('All images were saved successfully.');
        await dialog.locator('#finish-button').click();

        // Every student has a picture now, and the picture can be loaded from the server.
        for (const { login: studentLogin } of students) {
            const image = images.and(page.locator(`[data-login="${studentLogin}"]`));
            await expect(image, `${studentLogin} has a picture`).toHaveAttribute('data-has-image', 'true');
            const source = (await image.getAttribute('src'))!;
            const loaded = await page.request.get(source);
            expect(loaded.status(), `the picture of ${studentLogin} can be loaded`).toBe(200);
            expect(loaded.headers()['content-type']).toContain('image/');
            expect((await loaded.body()).length).toBeGreaterThan(0);
        }

        // Uploading the same file again replaces the pictures and changes nothing else.
        const again = await uploadImages(page);
        expect(again.result.numberOfUsersNotFound).toBe(0);
        expect(again.result.numberOfImagesSaved).toBe(students.length);
    });

    test('Pictures of students that are not registered for the exam are counted as not found', async ({ page, login, examAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        // Only two of the four students of the PDF are registered.
        for (const { login: studentLogin } of students.slice(0, 2)) {
            await examAPIRequests.registerStudentForExam(exam, { username: studentLogin, password: '' });
        }

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        const { dialog, result } = await uploadImages(page);
        expect(result.numberOfUsersNotFound).toBe(2);
        expect([...result.listOfExamUserRegistrationNumbers].sort()).toEqual(students.slice(2).map((student) => student.registrationNumber));
        expect(result.numberOfImagesSaved).toBe(2);
        await expect(dialog).not.toContainText('All images were saved successfully.');
        await expect(dialog).toContainText('Images saved');
        await dialog.locator('#finish-button').click();

        const images = page.getByTestId('student-image');
        await expect(images).toHaveCount(2);
        for (const { login: studentLogin } of students.slice(0, 2)) {
            await expect(images.and(page.locator(`[data-login="${studentLogin}"]`))).toHaveAttribute('data-has-image', 'true');
        }
    });
});
