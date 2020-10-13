import { browser, ExpectedConditions as ec, protractor, promise } from 'protractor';
import { NavBarPage, SignInPage } from '../../page-objects/jhi-page-objects';

import { SubmissionComponentsPage, SubmissionDeleteDialog, SubmissionUpdatePage } from './submission.page-object';

const expect = chai.expect;

describe('Submission e2e test', () => {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let submissionComponentsPage: SubmissionComponentsPage;
    let submissionUpdatePage: SubmissionUpdatePage;
    let submissionDeleteDialog: SubmissionDeleteDialog;

    before(async () => {
        await browser.get('/');
        navBarPage = new NavBarPage();
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing('admin', 'admin');
        await browser.wait(ec.visibilityOf(navBarPage.entityMenu), 5000);
    });

    it('should load Submissions', async () => {
        await navBarPage.goToEntity('submission');
        submissionComponentsPage = new SubmissionComponentsPage();
        await browser.wait(ec.visibilityOf(submissionComponentsPage.title), 5000);
        expect(await submissionComponentsPage.getTitle()).to.eq('artemisApp.submission.home.title');
        await browser.wait(ec.or(ec.visibilityOf(submissionComponentsPage.entities), ec.visibilityOf(submissionComponentsPage.noResult)), 1000);
    });

    it('should load create Submission page', async () => {
        await submissionComponentsPage.clickOnCreateButton();
        submissionUpdatePage = new SubmissionUpdatePage();
        expect(await submissionUpdatePage.getPageTitle()).to.eq('artemisApp.submission.home.createOrEditLabel');
        await submissionUpdatePage.cancel();
    });

    it('should create and save Submissions', async () => {
        const nbButtonsBeforeCreate = await submissionComponentsPage.countDeleteButtons();

        await submissionComponentsPage.clickOnCreateButton();

        await promise.all([
            submissionUpdatePage.setSubmissionDateInput('01/01/2001' + protractor.Key.TAB + '02:30AM'),
            submissionUpdatePage.typeSelectLastOption(),
            submissionUpdatePage.participationSelectLastOption(),
        ]);

        const selectedSubmitted = submissionUpdatePage.getSubmittedInput();
        if (await selectedSubmitted.isSelected()) {
            await submissionUpdatePage.getSubmittedInput().click();
            expect(await submissionUpdatePage.getSubmittedInput().isSelected(), 'Expected submitted not to be selected').to.be.false;
        } else {
            await submissionUpdatePage.getSubmittedInput().click();
            expect(await submissionUpdatePage.getSubmittedInput().isSelected(), 'Expected submitted to be selected').to.be.true;
        }
        expect(await submissionUpdatePage.getSubmissionDateInput()).to.contain('2001-01-01T02:30', 'Expected submissionDate value to be equals to 2000-12-31');
        const selectedExampleSubmission = submissionUpdatePage.getExampleSubmissionInput();
        if (await selectedExampleSubmission.isSelected()) {
            await submissionUpdatePage.getExampleSubmissionInput().click();
            expect(await submissionUpdatePage.getExampleSubmissionInput().isSelected(), 'Expected exampleSubmission not to be selected').to.be.false;
        } else {
            await submissionUpdatePage.getExampleSubmissionInput().click();
            expect(await submissionUpdatePage.getExampleSubmissionInput().isSelected(), 'Expected exampleSubmission to be selected').to.be.true;
        }

        await submissionUpdatePage.save();
        expect(await submissionUpdatePage.getSaveButton().isPresent(), 'Expected save button disappear').to.be.false;

        expect(await submissionComponentsPage.countDeleteButtons()).to.eq(nbButtonsBeforeCreate + 1, 'Expected one more entry in the table');
    });

    it('should delete last Submission', async () => {
        const nbButtonsBeforeDelete = await submissionComponentsPage.countDeleteButtons();
        await submissionComponentsPage.clickOnLastDeleteButton();

        submissionDeleteDialog = new SubmissionDeleteDialog();
        expect(await submissionDeleteDialog.getDialogTitle()).to.eq('artemisApp.submission.delete.question');
        await submissionDeleteDialog.clickOnConfirmButton();

        expect(await submissionComponentsPage.countDeleteButtons()).to.eq(nbButtonsBeforeDelete - 1);
    });

    after(async () => {
        await navBarPage.autoSignOut();
    });
});
