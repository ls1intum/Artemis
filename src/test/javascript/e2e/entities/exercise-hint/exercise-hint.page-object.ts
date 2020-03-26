import { by, element, ElementFinder } from 'protractor';

export class ExerciseHintComponentsPage {
    createButton = element(by.id('jh-create-entity'));
    deleteButtons = element.all(by.css('jhi-exercise-hint div table .btn-danger'));
    title = element.all(by.css('jhi-exercise-hint div h2#page-heading span')).first();

    async clickOnCreateButton(timeout?: number) {
        await this.createButton.click();
    }

    async clickOnLastDeleteButton(timeout?: number) {
        await this.deleteButtons.last().click();
    }

    async countDeleteButtons() {
        return this.deleteButtons.count();
    }

    async getTitle() {
        return this.title.getAttribute('jhiTranslate');
    }
}

export class ExerciseHintUpdatePage {
    pageTitle = element(by.id('jhi-exercise-hint-heading'));
    saveButton = element(by.id('save-entity'));
    cancelButton = element(by.id('cancel-save'));
    titleInput = element(by.id('field_title'));
    contentInput = element(by.id('field_content'));
    exerciseSelect = element(by.id('field_exercise'));

    async getPageTitle() {
        return this.pageTitle.getAttribute('jhiTranslate');
    }

    async setTitleInput(title) {
        await this.titleInput.sendKeys(title);
    }

    async getTitleInput() {
        return await this.titleInput.getAttribute('value');
    }

    async setContentInput(content) {
        await this.contentInput.sendKeys(content);
    }

    async getContentInput() {
        return await this.contentInput.getAttribute('value');
    }

    async exerciseSelectLastOption(timeout?: number) {
        await this.exerciseSelect.all(by.tagName('option')).last().click();
    }

    async exerciseSelectOption(option) {
        await this.exerciseSelect.sendKeys(option);
    }

    getExerciseSelect(): ElementFinder {
        return this.exerciseSelect;
    }

    async getExerciseSelectedOption() {
        return await this.exerciseSelect.element(by.css('option:checked')).getText();
    }

    async save(timeout?: number) {
        await this.saveButton.click();
    }

    async cancel(timeout?: number) {
        await this.cancelButton.click();
    }

    getSaveButton(): ElementFinder {
        return this.saveButton;
    }
}

export class ExerciseHintDeleteDialog {
    private dialogTitle = element(by.id('jhi-delete-exerciseHint-heading'));
    private confirmButton = element(by.id('jhi-confirm-delete-exerciseHint'));

    async getDialogTitle() {
        return this.dialogTitle.getAttribute('jhiTranslate');
    }

    async clickOnConfirmButton(timeout?: number) {
        await this.confirmButton.click();
    }
}
