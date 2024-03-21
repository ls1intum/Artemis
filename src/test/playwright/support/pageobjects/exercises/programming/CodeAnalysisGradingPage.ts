import { PROGRAMMING_EXERCISE_BASE } from '../../../constants';
import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for static code analysis grading configuration page.
 */
export class CodeAnalysisGradingPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async visit(courseId: number, exerciseId: number) {
        await this.page.goto(`course-management/${courseId}/programming-exercises/${exerciseId}/grading/code-analysis`);
    }

    async makeEveryScaCategoryInfluenceGrading() {
        // Wait for categories to load
        await this.page.waitForResponse('**/static-code-analysis-categories');
        // Using ids here would make the test more instable. Its unlikely that this selector will break in the future.
        const categories = this.page.locator('select');
        const numberOfCategories = await categories.count();

        for (let index = 0; index < numberOfCategories; index++) {
            await categories.nth(index).selectOption('GRADED');
        }
    }

    async saveChanges() {
        const responsePromise = this.page.waitForResponse(`${PROGRAMMING_EXERCISE_BASE}/*/static-code-analysis-categories`);
        await this.page.locator('#save-table-button').click();
        await responsePromise;
    }
}
