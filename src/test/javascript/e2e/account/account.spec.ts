import { browser, element, by, ExpectedConditions as ec } from 'protractor';

import { NavBarPage, SignInPage, PasswordPage, SettingsPage } from '../page-objects/jhi-page-objects';

const expect = chai.expect;

describe('account', () => {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let settingsPage: SettingsPage;

    beforeEach(async () => {
        await browser.get('/');
        navBarPage = new NavBarPage(true);
    });

    it('should fail to login with bad password', async () => {
        const expect1 = 'home.title';
        const value1 = await element(by.css('h1')).getAttribute('jhiTranslate');
        expect(value1).to.eq(expect1);
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_doesnotexist_user, process.env.bamboo_doesnotexist_password);

        const expect2 = 'Failed to sign in! Please check your username and password and try again.';
        const value2 = await element(by.className('alert-danger')).getText();
        expect(value2).to.eq(expect2);

        await signInPage.dismiss();
    });

    it('should login successfully with admin account', async () => {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_admin_user, process.env.bamboo_admin_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result) => expect(result).to.be.true);
    });

    it('should login successfully with instructor account', async () => {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_instructor_user, process.env.bamboo_instructor_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result) => expect(result).to.be.true);
    });

    it('should login successfully with tutor account', async () => {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_tutor_user, process.env.bamboo_tutor_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result) => expect(result).to.be.true);
    });

    it('should login successfully with student account', async () => {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_student_user, process.env.bamboo_student_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result) => expect(result).to.be.true);
    });

    afterEach(async () => {
        if (await navBarPage.signOut.isPresent()) {
            await navBarPage.autoSignOut();
        }
    });
});
