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

    async checkExamStudent(username: string) {
        await expect(this.page.locator('#student-exam').getByText(new RegExp(`\\s+${username}\\s+`))).toHaveCount(1);
    }

    getStudentExamRows() {
        return this.page.locator('#student-exam').locator('.datatable-body-row');
    }

    getStudentExamRow(username: string) {
        return this.getStudentExamRows().filter({ hasText: new RegExp(`\\s+${username}\\s+`) });
    }

    async getExamProperty(username: string, property: string) {
        const headers = this.page.locator('.datatable-header-cell');
        let propertyIndex: number | undefined;

        for (let index = 0; index < (await headers.count()); index++) {
            if (await headers.nth(index).filter({ hasText: property }).isVisible()) {
                propertyIndex = index;
                break;
            }
        }

        expect(propertyIndex).toBeDefined();
        return this.page.locator('.datatable-body-cell-label').nth(propertyIndex!);
    }

    async checkExamProperty(username: string, property: string, value: string) {
        const propertyLocator = await this.getExamProperty(username, property);
        await expect(propertyLocator).toContainText(value);
    }

    async typeSearchText(text: string) {
        const searchTextField = this.page.locator('#typeahead-basic');
        await searchTextField.clear();
        await searchTextField.fill(text);
    }
}
