import { element, by } from 'protractor';

export class CreateProgrammingExercisePage {
    save = element(by.id('save-entity'));
    cancel = element(by.id('cancel-save'));
    title = element(by.id('field_title'));
    shortName = element(by.id('field_shortName'));
    packageName = element(by.id('field_packageName'));
    maxScore = element(by.id('field_maxScore'));

    async setTitle(title: string) {
        await this.title.sendKeys(title);
    }

    async setShortName(shortName: string) {
        await this.shortName.sendKeys(shortName);
    }

    async setPackageName(packageName: string) {
        await this.packageName.sendKeys(packageName);
    }

    async setMaxScore(maxScore: string) {
        await this.maxScore.sendKeys(maxScore);
    }

    async clickSave() {
        await this.save.click();
    }

    async clickCancel() {
        await this.cancel.click();
    }
}
