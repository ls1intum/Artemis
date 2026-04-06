import { BASE_API, ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    async openAssessmentForComponent(componentNumber: number) {
        await this.page
            .locator('#apollon-assessment-row')
            .locator('.react-flow__node')
            .nth(componentNumber)
            .dblclick({ position: { x: 100, y: 5 }, force: true });
    }

    async assessComponent(points: number, feedback: string) {
        await this.getPointAssessmentField().fill(`${points}`);
        await this.getFeedbackAssessmentField().fill(`${feedback}`);
    }

    async clickNextAssessment() {
        await this.getNextAssessmentField().click();
    }

    rejectComplaint(response: string, examMode: false) {
        return super.rejectComplaint(response, examMode, ExerciseType.MODELING);
    }

    acceptComplaint(response: string, examMode: false) {
        return super.acceptComplaint(response, examMode, ExerciseType.MODELING);
    }

    async closeAssessmentPanel() {
        await this.page.keyboard.press('Escape');
        await this.page.locator('.MuiPopover-root').waitFor({ state: 'hidden' });
    }

    async submitExample() {
        await this.closeAssessmentPanel();
        await this.page.getByText('Save Example Assessment').click();
        await expect(this.page.getByText('Your assessment was saved successfully!')).toBeVisible({ timeout: 30000 });
    }

    async submit() {
        await this.closeAssessmentPanel();
        const responsePromise = this.page.waitForResponse(`${BASE_API}/modeling/modeling-submissions/*/result/*/assessment*`);
        await super.submitWithoutInterception();
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        return response;
    }

    private getNextAssessmentField() {
        return this.page.getByRole('button', { name: 'Next Assessment' });
    }

    private getPointAssessmentField() {
        return this.page.getByRole('spinbutton').first();
    }

    private getFeedbackAssessmentField() {
        return this.page.getByRole('textbox', { name: 'You can enter feedback here...' }).first();
    }
}
