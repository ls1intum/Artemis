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
        await this.page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Generate individual exams' }).last().click();
        const response = await responsePromise;
        await this.page.keyboard.press('Escape');
        return response;
    }

    async clickRegisterCourseStudents() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/register-course-students`);
        await this.page.getByRole('button', { name: 'Students' }).click();
        await this.page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Register course students' }).last().click();
        return await responsePromise;
    }

    async clickPrepareExerciseStart() {
        await this.openManageStudentExamsMenu();
        await this.page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Prepare exercise start' }).last().click();
    }

    async openManageStudentExamsMenu() {
        // The status popover trigger sits right next to this menu and is named "Individual exams status", which
        // contains this name; `getByRole` matches a substring by default, so the menu has to be matched exactly.
        const manageStudentExamsButton = this.page.getByRole('button', { name: 'Individual exams', exact: true });
        await expect(manageStudentExamsButton).toBeEnabled();
        await manageStudentExamsButton.click();
    }

    getGenerateMissingStudentExamsButton() {
        // The entry's disabled state sits on PrimeNG's list item, which wraps the label this menu projects.
        return this.page.getByTestId('exam-students-menu-entry').filter({ hasText: 'Generate missing individual exams' }).last();
    }

    getStudentExamRows() {
        return this.page
            .getByTestId('exam-students-table')
            .locator('tbody tr')
            .filter({ has: this.page.getByTestId('table-cell') });
    }

    private async checkPropertyValue(column: string, value: string, studentName: string) {
        const table = this.page.getByTestId('exam-students-table');
        await table.waitFor({ state: 'visible' });
        const row = table.locator('tbody tr', { hasText: studentName }).first();
        await expect(row.locator(`[data-testid="table-cell"][data-column="${column}"]`)).toContainText(value);
    }

    /** @param column the field of the column in the table, for example `progress` or `workingTime` */
    async checkStudentExamProperty(username: string, column: string, value: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        await this.checkPropertyValue(column, value, studentInfo.name!);
    }

    async checkStudent(username: string) {
        await expect(this.page.getByTestId('exam-students-table').locator('tbody tr', { hasText: username }).first()).toBeVisible();
    }

    async checkExamStudent(username: string) {
        const studentInfo = await users.getUserInfo(username, this.page);
        // Extend the default 10s expect timeout to 30s. Callers run this immediately after
        // `typeSearchText`, which fires a server-side filter request — under multi-node CI
        // load that round trip + the table re-render can exceed the default.
        await expect(this.page.getByTestId('exam-students-table').locator('tbody tr', { hasText: studentInfo.name! }).first()).toBeVisible({ timeout: 30000 });
    }

    async typeSearchText(text: string) {
        // The search field's own test id is the contract: the role and accessible name of its inner control are
        // implementation details of the field. The page applies the term after a short debounce.
        const searchTextField = this.page.getByTestId('exam-students-search').locator('input');
        await searchTextField.clear();
        await searchTextField.fill(text);
    }
}
