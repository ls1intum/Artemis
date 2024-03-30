import { Page, expect } from '@playwright/test';
import { Commands } from '../../commands';
import { hasAttributeWithValue } from '../../utils';

export class ExerciseAssessmentDashboardPage {
    private readonly page: Page;
    private complaintTextAreaSelector = '#complaintTextArea';

    constructor(page: Page) {
        this.page = page;
    }

    async clickHaveReadInstructionsButton() {
        await this.page.click('#participate-in-assessment');
    }

    async clickStartNewAssessment() {
        const startAssessingSelector = '#start-new-assessment';
        await Commands.reloadUntilFound(this.page, startAssessingSelector);
        await this.page.locator(startAssessingSelector).click();
    }

    async clickOpenAssessment() {
        await this.page.locator('#open-assessment').click();
    }

    async clickEvaluateComplaint() {
        await this.page.locator('#evaluate-complaint').click();
    }

    getComplaintText() {
        return this.page.locator(this.complaintTextAreaSelector);
    }

    getLockedMessage() {
        return this.page.locator('#assessmentLockedCurrentUser');
    }

    async checkComplaintText(complaintText: string) {
        await this.page.locator(this.complaintTextAreaSelector).waitFor({ state: 'attached' });
        expect(await hasAttributeWithValue(this.page, this.complaintTextAreaSelector, complaintText)).toBe(true);
    }
}
