import { BASE_API, ExerciseType, MODELING_EDITOR_CANVAS } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';
import { expect } from '@playwright/test';

const ASSESSMENT_CONTAINER = '#modeling-assessment-container';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    async openAssessmentForComponent(componentNumber: number) {
        await this.page
            .locator('#apollon-assessment-row')
            .locator(`${MODELING_EDITOR_CANVAS} >>> :nth-child(${componentNumber})`)
            .nth(0)
            .click({ clickCount: 2, position: { x: 0, y: 0 }, force: true });
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

    async submitExample() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}modeling-submissions/*/example-assessment`);
        await this.page.getByText('Save Example Assessment').click();
        const response = await responsePromise;
        expect(response.status()).toBe(200);
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}modeling-submissions/*/result/*/assessment*`);
        await super.submitWithoutInterception();
        const response = await responsePromise;
        expect(response.status()).toBe(200);
    }

    private getNextAssessmentField() {
        return this.getAssessmentContainer().getByRole('region').last();
    }

    private getPointAssessmentField() {
        return this.getAssessmentContainer().getByRole('region').nth(1).locator('div').locator('div').nth(1);
    }

    private getFeedbackAssessmentField() {
        return this.getAssessmentContainer().getByRole('region').nth(3);
    }

    private getAssessmentContainer() {
        return this.page.locator(`${ASSESSMENT_CONTAINER}`);
    }
}
