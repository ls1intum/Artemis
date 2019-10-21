import { by, element } from 'protractor';

export class ModelingExercisePage {
    createNewModelingExercise = element(by.id('modeling-exercise-create-button'));

    async clickOnCreateNewModelingExercise() {
        await this.createNewModelingExercise.click();
    }
}

export class NewModelingExercisePage {
    save = element(by.id('modeling-save-button'));
    cancelBack = element(by.id('modeling-back-cancel-button'));
    title = element(by.id('field_title'));
    maxScore = element(by.id('field_maxScore'));
    problemStatement = element(by.id('field_problemStatement'));

    async setTitle(title: string) {
        await this.title.sendKeys(title);
    }

    async setProblemStatement(statement: string) {
        await this.problemStatement.sendKeys(statement);
    }

    async setMaxScore(score: number) {
        await this.maxScore.sendKeys(score);
    }

    async clickSave() {
        await this.save.click();
    }

    async clickCancelBack() {
        await this.cancelBack.click();
    }
}
