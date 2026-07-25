import { TEXT_EXERCISE_BASE } from '../../../constants';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';
import { Dayjs } from 'dayjs';
import { fillDateTimePicker } from '../../../utils';

export class TextExerciseCreationPage extends AbstractExerciseCreationPage {
    private readonly PROBLEM_STATEMENT_SELECTOR = '#problemStatement';
    private readonly EXAMPLE_SOLUTION_SELECTOR = '#exampleSolution';
    private readonly ASSESSMENT_INSTRUCTIONS_SELECTOR = '#gradingInstructions';

    async typeMaxPoints(maxPoints: number) {
        await this.page.locator('#field_points').fill(maxPoints.toString());
    }

    async setReleaseDate(date: Dayjs) {
        await this.setTimelineDate('Release Date', date);
    }

    async setStartDate(date: Dayjs) {
        await this.setTimelineDate('Start Date', date);
    }

    async setDueDate(date: Dayjs) {
        await this.setTimelineDate('Due Date', date);
    }

    async setAssessmentDueDate(date: Dayjs) {
        await this.setTimelineDate('Assessment Due Date', date);
    }

    async typeProblemStatement(statement: string) {
        const textEditor = this.getTextEditorLocator(this.PROBLEM_STATEMENT_SELECTOR);
        await this.typeTextInMonaco(textEditor, statement);
    }

    async clearProblemStatement() {
        const textEditor = this.getTextEditorLocator(this.PROBLEM_STATEMENT_SELECTOR);
        await this.clearText(textEditor);
    }

    async typeExampleSolution(statement: string) {
        const textEditor = this.getTextEditorLocator(this.EXAMPLE_SOLUTION_SELECTOR);
        await this.typeTextInMonaco(textEditor, statement);
    }

    async clearExampleSolution() {
        const textEditor = this.getTextEditorLocator(this.EXAMPLE_SOLUTION_SELECTOR);
        await this.clearText(textEditor);
    }

    async typeAssessmentInstructions(statement: string) {
        const textEditor = this.getTextEditorLocator(this.ASSESSMENT_INSTRUCTIONS_SELECTOR);
        await this.typeTextInMonaco(textEditor, statement);
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
        const responsePromise = this.page.waitForResponse((response) => response.url().includes(`${TEXT_EXERCISE_BASE}/import?sourceExerciseId=`));
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    private getTextEditorLocator(selector: string) {
        // Return just the container - setMonacoEditorContentByLocator will find .monaco-editor inside
        return this.page.locator(selector);
    }

    private async setTimelineDate(label: string, date: Dayjs) {
        const dateInput = this.page.getByLabel(label, { exact: true });
        await dateInput.waitFor({ state: 'visible' });
        await fillDateTimePicker(dateInput, date);
    }
}
