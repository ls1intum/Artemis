/* tslint:disable no-unused-expression */
import { browser, ExpectedConditions as ec, promise } from 'protractor';
import { NavBarPage, SignInPage } from '../../page-objects/jhi-page-objects';

import { ExerciseHintComponentsPage, ExerciseHintDeleteDialog, ExerciseHintUpdatePage } from './exercise-hint.page-object';

const expect = chai.expect;

describe('ExerciseHint e2e test', () => {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let exerciseHintUpdatePage: ExerciseHintUpdatePage;
    let exerciseHintComponentsPage: ExerciseHintComponentsPage;
    let exerciseHintDeleteDialog: ExerciseHintDeleteDialog;

    before(async () => {
        await browser.get('/');
        navBarPage = new NavBarPage();
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing('admin', 'admin');
        await browser.wait(ec.visibilityOf(navBarPage.entityMenu), 5000);
    });

    it('should load ExerciseHints', async () => {
        await navBarPage.goToEntity('exercise-hint');
        exerciseHintComponentsPage = new ExerciseHintComponentsPage();
        await browser.wait(ec.visibilityOf(exerciseHintComponentsPage.title), 5000);
        expect(await exerciseHintComponentsPage.getTitle()).to.eq('artemisApp.exerciseHint.home.title');
    });

    it('should load create ExerciseHint page', async () => {
        await exerciseHintComponentsPage.clickOnCreateButton();
        exerciseHintUpdatePage = new ExerciseHintUpdatePage();
        expect(await exerciseHintUpdatePage.getPageTitle()).to.eq('artemisApp.exerciseHint.home.createOrEditLabel');
        await exerciseHintUpdatePage.cancel();
    });

    it('should create and save ExerciseHints', async () => {
        const nbButtonsBeforeCreate = await exerciseHintComponentsPage.countDeleteButtons();

        await exerciseHintComponentsPage.clickOnCreateButton();
        await promise.all([exerciseHintUpdatePage.setTitleInput('title'), exerciseHintUpdatePage.setContentInput('content'), exerciseHintUpdatePage.exerciseSelectLastOption()]);
        expect(await exerciseHintUpdatePage.getTitleInput()).to.eq('title', 'Expected Title value to be equals to title');
        expect(await exerciseHintUpdatePage.getContentInput()).to.eq('content', 'Expected Content value to be equals to content');
        await exerciseHintUpdatePage.save();
        expect(await exerciseHintUpdatePage.getSaveButton().isPresent(), 'Expected save button disappear').to.be.false;

        expect(await exerciseHintComponentsPage.countDeleteButtons()).to.eq(nbButtonsBeforeCreate + 1, 'Expected one more entry in the table');
    });

    it('should delete last ExerciseHint', async () => {
        const nbButtonsBeforeDelete = await exerciseHintComponentsPage.countDeleteButtons();
        await exerciseHintComponentsPage.clickOnLastDeleteButton();

        exerciseHintDeleteDialog = new ExerciseHintDeleteDialog();
        expect(await exerciseHintDeleteDialog.getDialogTitle()).to.eq('artemisApp.exerciseHint.delete.question');
        await exerciseHintDeleteDialog.clickOnConfirmButton();

        expect(await exerciseHintComponentsPage.countDeleteButtons()).to.eq(nbButtonsBeforeDelete - 1);
    });

    after(async () => {
        await navBarPage.autoSignOut();
    });
});
