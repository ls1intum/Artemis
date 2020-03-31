import { browser, by, element, ExpectedConditions as ec } from 'protractor';

import { NavBarPage, SettingsPage, SignInPage } from '../page-objects/jhi-page-objects';

const expect = chai.expect;

describe('account', function () {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let settingsPage: SettingsPage;

    beforeEach(async function () {
        await browser.get('/');
        navBarPage = new NavBarPage(true);
    });

    it('should fail to login with bad password', async function () {
        const expect1 = 'home.title';
        const value1 = await element(by.css('h1')).getAttribute('jhiTranslate');
        expect(value1).to.eq(expect1);
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_doesnotexist_user, process.env.bamboo_doesnotexist_password);

        const expect2 = 'Failed to sign in! Please check your username and password and try again.';
        const value2 = await element(by.className('alert-danger')).getText();
        expect(value2).to.eq(expect2);
    });

    it('should login successfully with admin account', async function () {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_admin_user, process.env.bamboo_admin_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result: any) => expect(result).to.be.true);
    });

    it('should login successfully with instructor account', async function () {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_instructor_user, process.env.bamboo_instructor_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result: any) => expect(result).to.be.true);
    });

    it('should login successfully with tutor account', async function () {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_tutor_user, process.env.bamboo_tutor_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result: any) => expect(result).to.be.true);
    });

    it('should login successfully with student account', async function () {
        await browser.get('/');
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing(process.env.bamboo_student_user, process.env.bamboo_student_password);

        browser.wait(ec.urlContains('/courses'), 5000).then((result: any) => expect(result).to.be.true);
    });

    afterEach(async function () {
        if (await navBarPage.signOut.isPresent()) {
            await navBarPage.autoSignOut();
        }
    });
});
