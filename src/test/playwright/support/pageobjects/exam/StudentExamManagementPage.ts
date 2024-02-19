import { Page, expect } from '@playwright/test';
import { COURSE_BASE } from '../../constants';

export class StudentExamManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async clickGenerateStudentExams() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}*/exams/*/generate-student-exams`);
        await this.page.click('#generateStudentExamsButton');
        await responsePromise;
    }

    async clickRegisterCourseStudents() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}*/exams/*/register-course-students`);
        await this.page.click('#register-course-students');
        return await responsePromise;
    }

    getGenerateStudentExamsButton() {
        return this.page.locator('#generateStudentExamsButton');
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

    private async checkPropertyValue(property: string, value: string, username?: string) {
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
        const options = username ? { hasText: username } : undefined;
        await expect(
            this.page.locator('.datatable-body-row', options).locator('.datatable-row-center').getByRole('cell').nth(propertyIndex!).filter({ hasText: value }),
        ).toBeVisible();
    }

    async checkStudentExamProperty(username: string, property: string, value: string) {
        await this.checkPropertyValue(property, value, username);
    }

    async checkExamStudent(username: string) {
        await this.checkPropertyValue('Login', username);
    }

    async typeSearchText(text: string) {
        const searchTextField = this.page.locator('#typeahead-basic');
        await searchTextField.clear();
        await searchTextField.fill(text);
    }
}
