import { browser, ExpectedConditions as ec, protractor, promise } from 'protractor';
import { NavBarPage, SignInPage } from '../../page-objects/jhi-page-objects';

import { ExerciseResultComponentsPage, ExerciseResultDeleteDialog, ExerciseResultUpdatePage } from './exercise-result.page-object';

const expect = chai.expect;

describe('ExerciseResult e2e test', () => {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let exerciseResultComponentsPage: ExerciseResultComponentsPage;
    let exerciseResultUpdatePage: ExerciseResultUpdatePage;
    let exerciseResultDeleteDialog: ExerciseResultDeleteDialog;

    before(async () => {
        await browser.get('/');
        navBarPage = new NavBarPage();
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing('admin', 'admin');
        await browser.wait(ec.visibilityOf(navBarPage.entityMenu), 5000);
    });

    it('should load ExerciseResults', async () => {
        await navBarPage.goToEntity('exercise-result');
        exerciseResultComponentsPage = new ExerciseResultComponentsPage();
        await browser.wait(ec.visibilityOf(exerciseResultComponentsPage.title), 5000);
        expect(await exerciseResultComponentsPage.getTitle()).to.eq('artemisApp.exerciseResult.home.title');
        await browser.wait(ec.or(ec.visibilityOf(exerciseResultComponentsPage.entities), ec.visibilityOf(exerciseResultComponentsPage.noResult)), 1000);
    });

    it('should load create ExerciseResult page', async () => {
        await exerciseResultComponentsPage.clickOnCreateButton();
        exerciseResultUpdatePage = new ExerciseResultUpdatePage();
        expect(await exerciseResultUpdatePage.getPageTitle()).to.eq('artemisApp.exerciseResult.home.createOrEditLabel');
        await exerciseResultUpdatePage.cancel();
    });

    it('should create and save ExerciseResults', async () => {
        const nbButtonsBeforeCreate = await exerciseResultComponentsPage.countDeleteButtons();

        await exerciseResultComponentsPage.clickOnCreateButton();

        await promise.all([
            exerciseResultUpdatePage.setResultStringInput('resultString'),
            exerciseResultUpdatePage.setCompletionDateInput('01/01/2001' + protractor.Key.TAB + '02:30AM'),
            exerciseResultUpdatePage.setScoreInput('5'),
            exerciseResultUpdatePage.assessmentTypeSelectLastOption(),
            exerciseResultUpdatePage.assessorSelectLastOption(),
            exerciseResultUpdatePage.submissionSelectLastOption(),
            exerciseResultUpdatePage.participationSelectLastOption(),
        ]);

        expect(await exerciseResultUpdatePage.getResultStringInput()).to.eq('resultString', 'Expected ResultString value to be equals to resultString');
        expect(await exerciseResultUpdatePage.getCompletionDateInput()).to.contain('2001-01-01T02:30', 'Expected completionDate value to be equals to 2000-12-31');
        const selectedSuccessful = exerciseResultUpdatePage.getSuccessfulInput();
        if (await selectedSuccessful.isSelected()) {
            await exerciseResultUpdatePage.getSuccessfulInput().click();
            expect(await exerciseResultUpdatePage.getSuccessfulInput().isSelected(), 'Expected successful not to be selected').to.be.false;
        } else {
            await exerciseResultUpdatePage.getSuccessfulInput().click();
            expect(await exerciseResultUpdatePage.getSuccessfulInput().isSelected(), 'Expected successful to be selected').to.be.true;
        }
        const selectedBuildArtifact = exerciseResultUpdatePage.getBuildArtifactInput();
        if (await selectedBuildArtifact.isSelected()) {
            await exerciseResultUpdatePage.getBuildArtifactInput().click();
            expect(await exerciseResultUpdatePage.getBuildArtifactInput().isSelected(), 'Expected buildArtifact not to be selected').to.be.false;
        } else {
            await exerciseResultUpdatePage.getBuildArtifactInput().click();
            expect(await exerciseResultUpdatePage.getBuildArtifactInput().isSelected(), 'Expected buildArtifact to be selected').to.be.true;
        }
        expect(await exerciseResultUpdatePage.getScoreInput()).to.eq('5', 'Expected score value to be equals to 5');
        const selectedRated = exerciseResultUpdatePage.getRatedInput();
        if (await selectedRated.isSelected()) {
            await exerciseResultUpdatePage.getRatedInput().click();
            expect(await exerciseResultUpdatePage.getRatedInput().isSelected(), 'Expected rated not to be selected').to.be.false;
        } else {
            await exerciseResultUpdatePage.getRatedInput().click();
            expect(await exerciseResultUpdatePage.getRatedInput().isSelected(), 'Expected rated to be selected').to.be.true;
        }
        const selectedHasFeedback = exerciseResultUpdatePage.getHasFeedbackInput();
        if (await selectedHasFeedback.isSelected()) {
            await exerciseResultUpdatePage.getHasFeedbackInput().click();
            expect(await exerciseResultUpdatePage.getHasFeedbackInput().isSelected(), 'Expected hasFeedback not to be selected').to.be.false;
        } else {
            await exerciseResultUpdatePage.getHasFeedbackInput().click();
            expect(await exerciseResultUpdatePage.getHasFeedbackInput().isSelected(), 'Expected hasFeedback to be selected').to.be.true;
        }
        const selectedHasComplaint = exerciseResultUpdatePage.getHasComplaintInput();
        if (await selectedHasComplaint.isSelected()) {
            await exerciseResultUpdatePage.getHasComplaintInput().click();
            expect(await exerciseResultUpdatePage.getHasComplaintInput().isSelected(), 'Expected hasComplaint not to be selected').to.be.false;
        } else {
            await exerciseResultUpdatePage.getHasComplaintInput().click();
            expect(await exerciseResultUpdatePage.getHasComplaintInput().isSelected(), 'Expected hasComplaint to be selected').to.be.true;
        }
        const selectedExampleResult = exerciseResultUpdatePage.getExampleResultInput();
        if (await selectedExampleResult.isSelected()) {
            await exerciseResultUpdatePage.getExampleResultInput().click();
            expect(await exerciseResultUpdatePage.getExampleResultInput().isSelected(), 'Expected exampleResult not to be selected').to.be.false;
        } else {
            await exerciseResultUpdatePage.getExampleResultInput().click();
            expect(await exerciseResultUpdatePage.getExampleResultInput().isSelected(), 'Expected exampleResult to be selected').to.be.true;
        }

        await exerciseResultUpdatePage.save();
        expect(await exerciseResultUpdatePage.getSaveButton().isPresent(), 'Expected save button disappear').to.be.false;

        expect(await exerciseResultComponentsPage.countDeleteButtons()).to.eq(nbButtonsBeforeCreate + 1, 'Expected one more entry in the table');
    });

    it('should delete last ExerciseResult', async () => {
        const nbButtonsBeforeDelete = await exerciseResultComponentsPage.countDeleteButtons();
        await exerciseResultComponentsPage.clickOnLastDeleteButton();

        exerciseResultDeleteDialog = new ExerciseResultDeleteDialog();
        expect(await exerciseResultDeleteDialog.getDialogTitle()).to.eq('artemisApp.exerciseResult.delete.question');
        await exerciseResultDeleteDialog.clickOnConfirmButton();

        expect(await exerciseResultComponentsPage.countDeleteButtons()).to.eq(nbButtonsBeforeDelete - 1);
    });

    after(async () => {
        await navBarPage.autoSignOut();
    });
});
