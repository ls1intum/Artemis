import { TEXT_EXERCISE_BASE } from '../../../constants';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';
import { Dayjs } from 'dayjs';
import { fillDateTimePicker } from '../../../utils';

export class TextExerciseCreationPage extends AbstractExerciseCreationPage {
    private readonly PROBLEM_STATEMENT_SELECTOR = '#problemStatement';
    private readonly EXAMPLE_SOLUTION_SELECTOR = '#exampleSolution';
    private readonly ASSESSMENT_INSTRUCTIONS_SELECTOR = '#gradingInstructions';
    private readonly TIMELINE_SELECTOR = 'jhi-text-exercise-timeline';

    async typeMaxPoints(maxPoints: number) {
        await this.page.locator('#field_points').fill(maxPoints.toString());
    }

    override async setReleaseDate(date: Dayjs) {
        await this.setTimelineDate(0, date);
    }

    async setStartDate(date: Dayjs) {
        await this.setTimelineDate(1, date);
    }

    override async setDueDate(date: Dayjs) {
        await this.setTimelineDate(2, date);
    }

    override async setAssessmentDueDate(date: Dayjs) {
        await this.setTimelineDate(3, date);
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

    private async setTimelineDate(index: number, date: Dayjs) {
        const dateInput = this.page.locator(this.TIMELINE_SELECTOR).locator(`#datepicker-${index}`);
        await dateInput.waitFor({ state: 'visible' });
        await fillDateTimePicker(dateInput, date);
    }
}
