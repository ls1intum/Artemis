import { Page } from '@playwright/test';
import { Commands } from '../../commands';

export class ExerciseAssessmentDashboardPage {
    private readonly page: Page;

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
        return this.page.locator('#complaintTextArea');
    }

    getLockedMessage() {
        return this.page.locator('#assessmentLockedCurrentUser');
    }
}
