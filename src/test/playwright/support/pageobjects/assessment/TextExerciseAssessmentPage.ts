import { BASE_API, ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the text exercise assessment page.
 */
export class TextExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return this.page.locator('#instructions-card');
    }

    async provideFeedbackOnTextSection(sectionIndex: number, points: number, feedback: string) {
        await this.getFeedbackSection(sectionIndex).click();
        await this.typeIntoFeedbackEditor(sectionIndex, feedback);
        await this.typePointsIntoFeedbackEditor(sectionIndex, points);
    }

    private async typeIntoFeedbackEditor(sectionIndex: number, feedbackText: string) {
        await this.getFeedbackSection(sectionIndex).locator('#feedback-editor-text-input').fill(feedbackText);
    }

    private async typePointsIntoFeedbackEditor(sectionIndex: number, feedbackPoints: number) {
        const textField = this.getFeedbackSection(sectionIndex).locator('#feedback-editor-points-input');
        await textField.clear();
        await textField.fill(feedbackPoints.toString());
    }

    private getFeedbackSection(sectionIndex: number) {
        return this.page.locator(`#text-feedback-block-${sectionIndex}`);
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/participations/*/results/*/submit-text-assessment`);
        await this.page.locator('#submit').click();
        return await responsePromise;
    }

    async rejectComplaint(response: string, examMode: boolean) {
        return await super.rejectComplaint(response, examMode, ExerciseType.TEXT);
    }

    async acceptComplaint(response: string, examMode: boolean) {
        return await super.acceptComplaint(response, examMode, ExerciseType.TEXT);
    }

    getWordCountElement() {
        return this.page.locator('#text-assessment-word-count');
    }

    getCharacterCountElement() {
        return this.page.locator('#text-assessment-character-count');
    }
}
