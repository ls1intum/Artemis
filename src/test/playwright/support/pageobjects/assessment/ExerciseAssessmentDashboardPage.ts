import { Page, expect } from '@playwright/test';
import { Commands } from '../../commands';

export class ExerciseAssessmentDashboardPage {
    private readonly page: Page;
    private complaintTextAreaSelector = '#complaintTextArea';

    constructor(page: Page) {
        this.page = page;
    }

    async clickHaveReadInstructionsButton() {
        await this.page.click('#participate-in-assessment');
    }

    async clickStartNewAssessment(assessmentRound: number = 1) {
        const startAssessingButton = this.page.locator('#start-new-assessment').nth(assessmentRound - 1);
        await Commands.reloadUntilFound(this.page, startAssessingButton);
        await startAssessingButton.click();
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
        await expect(this.getComplaintText()).toHaveValue(complaintText);
    }

    async toggleSecondCorrectionRound() {
        await this.page.getByTestId('toggle-second-correction').click();
    }
}
