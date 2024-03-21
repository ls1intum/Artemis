import { Page, expect } from '@playwright/test';
import { COURSE_BASE } from '../../constants';
import { users } from '../../users';

export class StudentExamManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async clickGenerateStudentExams() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/generate-student-exams`);
        await this.page.click('#generateStudentExamsButton');
        await responsePromise;
    }

    async clickRegisterCourseStudents() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/register-course-students`);
        await this.page.click('#register-course-students');
        return await responsePromise;
    }

    getGenerateMissingStudentExamsButton() {
        return this.page.locator('#generateMissingStudentExamsButton');
    }

    getRegisteredStudents() {
        return this.page.locator('#registered-students');
    }

    getStudentExamRows() {
        return this.page.locator('#student-exam').locator('.datatable-body-row');
    }

    getStudentExamRow(username: string) {
        return this.getStudentExamRows().filter({ hasText: new RegExp(`\\s+${username}\\s+`) });
    }

    private async checkPropertyValue(property: string, value: string, studentName: string) {
        await this.page.locator('.data-table-container').waitFor({ state: 'visible' });
        const headers = this.page.locator('.datatable-header-cell');
        let propertyIndex: number | undefined;

        for (let index = 0; index < (await headers.count()); index++) {
            if (await headers.nth(index).filter({ hasText: property }).isVisible()) {
                propertyIndex = index;
                break;
            }
        }

        expect(propertyIndex).toBeDefined();
        await expect(
            this.page.locator('.datatable-body-row', { hasText: studentName }).locator('.datatable-row-center').getByRole('cell').nth(propertyIndex!).getByText(value),
        ).toBeVisible();
    }

    async checkStudentExamProperty(username: string, property: string, value: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        await this.checkPropertyValue(property, value, studentInfo.name!);
    }

    async checkStudent(username: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        await this.checkPropertyValue('Login', username, studentInfo.name!);
    }

    async checkExamStudent(username: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        await this.checkPropertyValue('Student', studentInfo.name!, studentInfo.name!);
    }

    async typeSearchText(text: string) {
        const searchTextField = this.page.locator('#typeahead-basic');
        await searchTextField.clear();
        await searchTextField.fill(text);
    }
}
