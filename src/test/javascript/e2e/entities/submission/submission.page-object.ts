import { element, by, ElementFinder } from 'protractor';

export class SubmissionComponentsPage {
    createButton = element(by.id('jh-create-entity'));
    deleteButtons = element.all(by.css('jhi-submission div table .btn-danger'));
    title = element.all(by.css('jhi-submission div h2#page-heading span')).first();
    noResult = element(by.id('no-result'));
    entities = element(by.id('entities'));

    async clickOnCreateButton(): Promise<void> {
        await this.createButton.click();
    }

    async clickOnLastDeleteButton(): Promise<void> {
        await this.deleteButtons.last().click();
    }

    async countDeleteButtons(): Promise<number> {
        return this.deleteButtons.count();
    }

    async getTitle(): Promise<string> {
        return this.title.getAttribute('jhiTranslate');
    }
}

export class SubmissionUpdatePage {
    pageTitle = element(by.id('jhi-submission-heading'));
    saveButton = element(by.id('save-entity'));
    cancelButton = element(by.id('cancel-save'));

    submittedInput = element(by.id('field_submitted'));
    submissionDateInput = element(by.id('field_submissionDate'));
    typeSelect = element(by.id('field_type'));
    exampleSubmissionInput = element(by.id('field_exampleSubmission'));

    participationSelect = element(by.id('field_participation'));

    async getPageTitle(): Promise<string> {
        return this.pageTitle.getAttribute('jhiTranslate');
    }

    getSubmittedInput(): ElementFinder {
        return this.submittedInput;
    }

    async setSubmissionDateInput(submissionDate: string): Promise<void> {
        await this.submissionDateInput.sendKeys(submissionDate);
    }

    async getSubmissionDateInput(): Promise<string> {
        return await this.submissionDateInput.getAttribute('value');
    }

    async setTypeSelect(type: string): Promise<void> {
        await this.typeSelect.sendKeys(type);
    }

    async getTypeSelect(): Promise<string> {
        return await this.typeSelect.element(by.css('option:checked')).getText();
    }

    async typeSelectLastOption(): Promise<void> {
        await this.typeSelect.all(by.tagName('option')).last().click();
    }

    getExampleSubmissionInput(): ElementFinder {
        return this.exampleSubmissionInput;
    }

    async participationSelectLastOption(): Promise<void> {
        await this.participationSelect.all(by.tagName('option')).last().click();
    }

    async participationSelectOption(option: string): Promise<void> {
        await this.participationSelect.sendKeys(option);
    }

    getParticipationSelect(): ElementFinder {
        return this.participationSelect;
    }

    async getParticipationSelectedOption(): Promise<string> {
        return await this.participationSelect.element(by.css('option:checked')).getText();
    }

    async save(): Promise<void> {
        await this.saveButton.click();
    }

    async cancel(): Promise<void> {
        await this.cancelButton.click();
    }

    getSaveButton(): ElementFinder {
        return this.saveButton;
    }
}

export class SubmissionDeleteDialog {
    private dialogTitle = element(by.id('jhi-delete-submission-heading'));
    private confirmButton = element(by.id('jhi-confirm-delete-submission'));

    async getDialogTitle(): Promise<string> {
        return this.dialogTitle.getAttribute('jhiTranslate');
    }

    async clickOnConfirmButton(): Promise<void> {
        await this.confirmButton.click();
    }
}
