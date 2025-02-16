import { Locator } from '@playwright/test';
import { TEXT_EXERCISE_BASE } from '../../../constants';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';

export class TextExerciseCreationPage extends AbstractExerciseCreationPage {
    private readonly PROBLEM_STATEMENT_SELECTOR = '#problemStatement';
    private readonly EXAMPLE_SOLUTION_SELECTOR = '#exampleSolution';
    private readonly ASSESSMENT_INSTRUCTIONS_SELECTOR = '#gradingInstructions';

    async typeMaxPoints(maxPoints: number) {
        await this.page.locator('#field_points').fill(maxPoints.toString());
    }

    async typeProblemStatement(statement: string) {
        const textEditor = this.getTextEditorLocator(this.PROBLEM_STATEMENT_SELECTOR);
        await this.typeText(textEditor, statement);
    }

    async clearProblemStatement() {
        const textEditor = this.getTextEditorLocator(this.PROBLEM_STATEMENT_SELECTOR);
        await this.clearText(textEditor);
    }

    async typeExampleSolution(statement: string) {
        const textEditor = this.getTextEditorLocator(this.EXAMPLE_SOLUTION_SELECTOR);
        await this.typeText(textEditor, statement);
    }

    async clearExampleSolution() {
        const textEditor = this.getTextEditorLocator(this.EXAMPLE_SOLUTION_SELECTOR);
        await this.clearText(textEditor);
    }

    async typeAssessmentInstructions(statement: string) {
        const textEditor = this.getTextEditorLocator(this.ASSESSMENT_INSTRUCTIONS_SELECTOR);
        await this.typeText(textEditor, statement);
    }

    async clearAssessmentInstructions() {
        const textEditor = this.getTextEditorLocator(this.ASSESSMENT_INSTRUCTIONS_SELECTOR);
        await this.clearText(textEditor);
    }

    async create() {
        const responsePromise = this.page.waitForResponse(TEXT_EXERCISE_BASE);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${TEXT_EXERCISE_BASE}/import/*`);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    private getTextEditorLocator(selector: string) {
        return this.page.locator(selector).locator('.monaco-editor');
    }

    private async typeText(textEditor: Locator, text: string) {
        await textEditor.click();
        await textEditor.pressSequentially(text);
    }
}
