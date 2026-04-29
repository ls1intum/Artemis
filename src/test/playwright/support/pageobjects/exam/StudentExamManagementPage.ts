import { Page, expect } from '@playwright/test';
import { users } from '../../users';

export class StudentExamManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async clickGenerateStudentExams() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/generate-student-exams`);
        await this.openManageStudentExamsMenu();
        await this.page.locator('.p-menu-item-link', { hasText: 'Generate individual exams' }).last().click();
        await responsePromise;
        await this.page.keyboard.press('Escape');
    }

    async clickRegisterCourseStudents() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/register-course-students`);
        await this.page.getByRole('button', { name: 'Register students' }).click();
        await this.page.locator('.p-menu-item-link', { hasText: 'Register course students' }).last().click();
        return await responsePromise;
    }

    async clickPrepareExerciseStart() {
        await this.openManageStudentExamsMenu();
        await this.page.locator('.p-menu-item-link', { hasText: 'Prepare exercise start' }).last().click();
    }

    async openManageStudentExamsMenu() {
        const manageStudentExamsButton = this.page.getByRole('button', { name: 'Manage individual exams' });
        await expect(manageStudentExamsButton).toBeEnabled();
        await manageStudentExamsButton.click();
    }

    getGenerateMissingStudentExamsButton() {
        return this.page.locator('.p-menu-item:has(.p-menu-item-link:has-text("Generate missing individual exams"))').last();
    }

    getStudentExamRows() {
        return this.page.locator('p-table tbody tr');
    }

    private async checkPropertyValue(property: string, value: string, studentName: string) {
        const table = this.page.locator('p-table').first();
        await table.waitFor({ state: 'visible' });
        const headers = table.locator('thead th');
        let propertyIndex: number | undefined;

        for (let index = 0; index < (await headers.count()); index++) {
            if (await headers.nth(index).filter({ hasText: property }).isVisible()) {
                propertyIndex = index;
                break;
            }
        }

        expect(propertyIndex).toBeDefined();
        const row = table.locator('tbody tr', { hasText: studentName }).first();
        await expect(row.locator('td').nth(propertyIndex!)).toContainText(value);
    }

    async checkStudentExamProperty(username: string, property: string, value: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        await this.checkPropertyValue(property, value, studentInfo.name!);
    }

    async checkStudent(username: string) {
        await expect(this.page.locator('p-table tbody tr', { hasText: username }).first()).toBeVisible();
    }

    async checkExamStudent(username: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        await expect(this.page.locator('p-table tbody tr', { hasText: studentInfo.name! }).first()).toBeVisible();
    }

    async typeSearchText(text: string) {
        const searchTextField = this.page.locator('input[placeholder="Search for registered students"]');
        await searchTextField.clear();
        await searchTextField.fill(text);
    }
}
