import { Page, expect } from '@playwright/test';
import { Commands } from '../../commands';

export class ExerciseAssessmentDashboardPage {
    private readonly page: Page;
    private complaintTextAreaSelector = '#complaintTextArea';

    constructor(page: Page) {
        this.page = page;
    }

    async clickHaveReadInstructionsButton() {
        const participateButton = this.page.locator('[data-testid="participate-in-assessment"]');
        await Commands.reloadUntilFound(this.page, participateButton);
        await participateButton.click();
    }

    async clickStartNewAssessment(assessmentRound: number = 1) {
        const startAssessingButton = this.page
            .locator(`[data-testid="manage-assessment-link"][data-assessment-action="assess"][data-correction-round="${assessmentRound - 1}"]`)
            .first();
        await Commands.reloadUntilFound(this.page, startAssessingButton);
        await startAssessingButton.click();
    }

    async clickOpenAssessment() {
        await this.page.locator('[data-testid="manage-assessment-link"][data-assessment-action="open"]').first().click();
    }

    async clickEvaluateComplaint() {
        await this.page.locator('[data-testid="evaluate-complaint"]').click();
    }

    getComplaintText() {
        return this.page.locator(this.complaintTextAreaSelector);
    }

    getLockedMessage() {
        return this.page.locator('[data-testid="assessmentLockedCurrentUser"]');
    }

    async checkComplaintText(complaintText: string) {
        await expect(this.getComplaintText()).toHaveValue(complaintText);
    }

    async toggleSecondCorrectionRound() {
        await this.page.getByTestId('toggle-second-correction').getByRole('switch').click();
    }
}
