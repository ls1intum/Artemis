import { Locator, Page } from '@playwright/test';
import { Dayjs } from 'dayjs';
import { enterDate } from '../../../utils';
import { TEXT_EXERCISE_BASE } from '../../../constants';

export class TextExerciseCreationPage {
    private readonly page: Page;

    private readonly PROBLEM_STATEMENT_SELECTOR = '#problemStatement';
    private readonly EXAMPLE_SOLUTION_SELECTOR = '#exampleSolution';
    private readonly ASSESSMENT_INSTRUCTIONS_SELECTOR = '#gradingInstructions';

    constructor(page: Page) {
        this.page = page;
    }

    async typeTitle(title: string) {
        const titleField = this.page.locator('#field_title');
        await titleField.clear();
        await titleField.fill(title);
    }

    async setReleaseDate(date: Dayjs) {
        await enterDate(this.page, '#pick-releaseDate', date);
    }

    async setDueDate(date: Dayjs) {
        await enterDate(this.page, '#pick-dueDate', date);
    }

    async setAssessmentDueDate(date: Dayjs) {
        await enterDate(this.page, '#pick-assessmentDueDate', date);
    }

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

    private async clearText(textEditor: Locator) {
        await textEditor.click();
        await textEditor.press('Control+a');
        await textEditor.press('Delete');
    }

    private async typeText(textEditor: Locator, text: string) {
        await textEditor.click();
        await textEditor.pressSequentially(text);
    }
}
