import { Page } from '@playwright/test';
import { clearTextField, enterDate } from '../../../utils';
import { Dayjs } from 'dayjs';
import { BASE_API, QUIZ_EXERCISE_BASE } from '../../../constants';
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
        const textInputField = this.page.locator('.ace_text-input');
        await textInputField.focus();
        await textInputField.pressSequentially(fileContent!);
    }

    async addShortAnswerQuestion(title: string) {
        await this.page.locator('#quiz-add-short-answer-question').click();
        await this.page.locator('#short-answer-question-title').fill(title);

        const fileContent = await Fixtures.get('exercise/quiz/short_answer/question.txt');
        const textInputField = this.page.locator('.ace_text-input');
        await clearTextField(textInputField);
        await this.page.locator('.ace_text-input').fill(fileContent!);
        await this.page.locator('#short-answer-show-visual').click();
    }

    async addDragAndDropQuestion(title: string) {
        await this.page.locator('#quiz-add-dnd-question').click();
        await this.page.locator('#drag-and-drop-question-title').fill(title);

        await this.uploadDragAndDropBackground();
        await this.page.mouse.move(50, 50);
        await this.page.mouse.down();
        await this.page.mouse.move(500, 300);
        await this.page.mouse.up();

        await this.createDragAndDropItem('Rick Astley');
        const dragLocator = this.page.locator('#drag-item-0');
        const dropLocator = this.page.locator('#drop-location');
        await dragLocator.dragTo(dropLocator);

        const fileContent = await Fixtures.get('fixtures/exercise/quiz/drag_and_drop/question.txt');
        const textInputField = this.page.locator('.ace_text-input');
        await clearTextField(textInputField);
        await textInputField.fill(fileContent!);
    }

    async createDragAndDropItem(text: string) {
        await this.page.locator('#add-text-drag-item').click();
        const dragItem = this.page.locator('#drag-item-0-text');
        await dragItem.clear();
        await dragItem.fill(text);
    }

    async uploadDragAndDropBackground() {
        const fileChooserPromise = this.page.waitForEvent('filechooser');
        const fileUploadPromise = this.page.waitForResponse(`${BASE_API}/fileUpload*`);
        await this.page.locator('#background-image-input-form').click();
        const fileChooser = await fileChooserPromise;
        await fileChooser.setFiles('exercise/quiz/drag_and_drop/background.jpg');
        await fileUploadPromise;
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
