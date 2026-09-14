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
        await responsePromise;
        await this.page.keyboard.press('Escape');
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
        // Extend the default 10s expect timeout to 30s. Callers run this immediately after
        // `typeSearchText`, which fires a server-side filter request — under multi-node CI
        // load that round trip + the PrimeNG p-table re-render can exceed the default.
        await expect(this.page.locator('p-table tbody tr', { hasText: studentInfo.name! }).first()).toBeVisible({ timeout: 30000 });
    }

    async typeSearchText(text: string) {
        // The exam students page renders the shared search-filter component. Its own test id is the contract:
        // both the role and the accessible name of the inner control are implementation details of the field,
        // and targeting either broke this test when the component changed.
        const searchTextField = this.page.locator('[data-testid="search-filter"] input');
        await searchTextField.clear();
        await searchTextField.fill(text);
    }
}
