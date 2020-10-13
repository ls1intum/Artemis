import { element, by, ElementFinder } from 'protractor';

export class ExerciseResultComponentsPage {
    createButton = element(by.id('jh-create-entity'));
    deleteButtons = element.all(by.css('jhi-exercise-result div table .btn-danger'));
    title = element.all(by.css('jhi-exercise-result div h2#page-heading span')).first();
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

export class ExerciseResultUpdatePage {
    pageTitle = element(by.id('jhi-exercise-result-heading'));
    saveButton = element(by.id('save-entity'));
    cancelButton = element(by.id('cancel-save'));

    resultStringInput = element(by.id('field_resultString'));
    completionDateInput = element(by.id('field_completionDate'));
    successfulInput = element(by.id('field_successful'));
    buildArtifactInput = element(by.id('field_buildArtifact'));
    scoreInput = element(by.id('field_score'));
    ratedInput = element(by.id('field_rated'));
    hasFeedbackInput = element(by.id('field_hasFeedback'));
    assessmentTypeSelect = element(by.id('field_assessmentType'));
    hasComplaintInput = element(by.id('field_hasComplaint'));
    exampleResultInput = element(by.id('field_exampleResult'));

    assessorSelect = element(by.id('field_assessor'));
    submissionSelect = element(by.id('field_submission'));
    participationSelect = element(by.id('field_participation'));

    async getPageTitle(): Promise<string> {
        return this.pageTitle.getAttribute('jhiTranslate');
    }

    async setResultStringInput(resultString: string): Promise<void> {
        await this.resultStringInput.sendKeys(resultString);
    }

    async getResultStringInput(): Promise<string> {
        return await this.resultStringInput.getAttribute('value');
    }

    async setCompletionDateInput(completionDate: string): Promise<void> {
        await this.completionDateInput.sendKeys(completionDate);
    }

    async getCompletionDateInput(): Promise<string> {
        return await this.completionDateInput.getAttribute('value');
    }

    getSuccessfulInput(): ElementFinder {
        return this.successfulInput;
    }

    getBuildArtifactInput(): ElementFinder {
        return this.buildArtifactInput;
    }

    async setScoreInput(score: string): Promise<void> {
        await this.scoreInput.sendKeys(score);
    }

    async getScoreInput(): Promise<string> {
        return await this.scoreInput.getAttribute('value');
    }

    getRatedInput(): ElementFinder {
        return this.ratedInput;
    }

    getHasFeedbackInput(): ElementFinder {
        return this.hasFeedbackInput;
    }

    async setAssessmentTypeSelect(assessmentType: string): Promise<void> {
        await this.assessmentTypeSelect.sendKeys(assessmentType);
    }

    async getAssessmentTypeSelect(): Promise<string> {
        return await this.assessmentTypeSelect.element(by.css('option:checked')).getText();
    }

    async assessmentTypeSelectLastOption(): Promise<void> {
        await this.assessmentTypeSelect.all(by.tagName('option')).last().click();
    }

    getHasComplaintInput(): ElementFinder {
        return this.hasComplaintInput;
    }

    getExampleResultInput(): ElementFinder {
        return this.exampleResultInput;
    }

    async assessorSelectLastOption(): Promise<void> {
        await this.assessorSelect.all(by.tagName('option')).last().click();
    }

    async assessorSelectOption(option: string): Promise<void> {
        await this.assessorSelect.sendKeys(option);
    }

    getAssessorSelect(): ElementFinder {
        return this.assessorSelect;
    }

    async getAssessorSelectedOption(): Promise<string> {
        return await this.assessorSelect.element(by.css('option:checked')).getText();
    }

    async submissionSelectLastOption(): Promise<void> {
        await this.submissionSelect.all(by.tagName('option')).last().click();
    }

    async submissionSelectOption(option: string): Promise<void> {
        await this.submissionSelect.sendKeys(option);
    }

    getSubmissionSelect(): ElementFinder {
        return this.submissionSelect;
    }

    async getSubmissionSelectedOption(): Promise<string> {
        return await this.submissionSelect.element(by.css('option:checked')).getText();
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

export class ExerciseResultDeleteDialog {
    private dialogTitle = element(by.id('jhi-delete-exerciseResult-heading'));
    private confirmButton = element(by.id('jhi-confirm-delete-exerciseResult'));

    async getDialogTitle(): Promise<string> {
        return this.dialogTitle.getAttribute('jhiTranslate');
    }

    async clickOnConfirmButton(): Promise<void> {
        await this.confirmButton.click();
    }
}
