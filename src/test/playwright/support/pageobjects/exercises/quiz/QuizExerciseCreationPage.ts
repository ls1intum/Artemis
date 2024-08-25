import { Page, expect } from '@playwright/test';
import { clearTextField, drag, enterDate } from '../../../utils';
import { Dayjs } from 'dayjs';
import { QUIZ_EXERCISE_BASE } from '../../../constants';
import { Fixtures } from '../../../../fixtures/fixtures';

export class QuizExerciseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    async setVisibleFrom(date: Dayjs) {
        await enterDate(this.page, '#pick-releaseDate', date);
    }

    async addMultipleChoiceQuestion(title: string, points = 1) {
        await this.page.locator('#quiz-add-mc-question').click();
        await this.page.locator('#mc-question-title').fill(title);
        await this.page.locator('#score').fill(points.toString());

        const fileContent = await Fixtures.get('exercise/quiz/multiple_choice/question.txt');
        const textInputField = this.page.locator('.monaco-editor');
        await textInputField.click();
        await textInputField.pressSequentially(fileContent!);
    }

    async addShortAnswerQuestion(title: string) {
        await this.page.locator('#quiz-add-short-answer-question').click();
        await this.page.locator('#short-answer-question-title').fill(title);

        const fileContent = await Fixtures.get('exercise/quiz/short_answer/question.txt');
        const textInputField = this.page.locator('.monaco-editor');
        await clearTextField(textInputField);
        await this.page.locator('.monaco-editor textarea').fill(fileContent!);
        await this.page.locator('#short-answer-show-visual').click();
    }

    async addDragAndDropQuestion(title: string) {
        await this.page.locator('#quiz-add-dnd-question').click();
        await this.page.locator('#drag-and-drop-question-title').fill(title);

        await this.uploadDragAndDropBackground();
        const element = this.page.locator('.background-area');
        const boundingBox = await element?.boundingBox();

        expect(boundingBox, { message: 'Could not get bounding box of element' }).not.toBeNull();
        await this.page.mouse.move(boundingBox.x + 800, boundingBox.y + 10);
        await this.page.mouse.down();
        await this.page.mouse.move(boundingBox.x + 1000, boundingBox.y + 150);
        await this.page.mouse.up();

        await this.createDragAndDropItem('Rick Astley');
        const dragLocator = this.page.locator('#drag-item-0');
        const dropLocator = this.page.locator('#drop-location');
        await drag(this.page, dragLocator, dropLocator);

        const fileContent = await Fixtures.get('exercise/quiz/drag_and_drop/question.txt');
        const textInputField = this.page.locator('.monaco-editor');
        await clearTextField(textInputField);
        await textInputField.pressSequentially(fileContent!);
    }

    async createDragAndDropItem(text: string) {
        await this.page.locator('#add-text-drag-item').click();
        const dragItem = this.page.locator('#drag-item-0-text');
        await dragItem.clear();
        await dragItem.fill(text);
    }

    async uploadDragAndDropBackground() {
        const fileChooserPromise = this.page.waitForEvent('filechooser');
        await this.page.locator('#background-file-input-button').click();
        const fileChooser = await fileChooserPromise;
        await fileChooser.setFiles('./fixtures/exercise/quiz/drag_and_drop/background.jpg');
    }

    async saveQuiz() {
        const responsePromise = this.page.waitForResponse(QUIZ_EXERCISE_BASE);
        await this.page.locator('#quiz-save').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${QUIZ_EXERCISE_BASE}/import/*`);
        await this.page.locator('#quiz-save').click();
        return await responsePromise;
    }
}
