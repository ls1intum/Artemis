import { element, by } from 'protractor';

export class QuizExercisePage {
    createNewQuiz = element(by.id('create-quiz-button'));

    async clickOnCreateNewQuiz() {
        await this.createNewQuiz.click();
    }
}

export class NewQuizExercisePage {
    save = element(by.id('quiz-save'));
    cancelBack = element(by.id('quiz-cancel-back-button'));
    title = element(by.id('field_title'));
    timeInMinutesInput = element(by.id('quiz-duration-minutes'));
    timeInSecondsInput = element(by.id('quiz-duration-seconds'));

    async setTitle(title: string) {
        await this.title.sendKeys(title);
    }

    async setTimeInMinutes(timeInMinutes: string) {
        await this.timeInMinutesInput.sendKeys(timeInMinutes);
    }

    async setTimeInSeconds(timeInSeconds: string) {
        await this.timeInSecondsInput.sendKeys(timeInSeconds);
    }

    async clickSave() {
        await this.save.click();
    }

    async clickCancelBack() {
        await this.cancelBack.click();
    }
}
