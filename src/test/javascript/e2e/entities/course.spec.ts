import { NavBarPage, SignInPage } from '../page-objects/jhi-page-objects';
import {browser, by, element} from "protractor";
import {CoursePage, NewCoursePage} from "../page-objects/entities/course-page-object";

const expect = chai.expect;

describe('course', () => {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let coursePage: CoursePage;
    let newCoursePage: NewCoursePage;

    let courseName: string;

    before(async () => {
        await browser.get('/');
        navBarPage = new NavBarPage(true);
        signInPage = await navBarPage.getSignInPage();
        coursePage = new CoursePage();
        newCoursePage = new NewCoursePage();
        courseName = `Protractor${Date.now()}`;
        await signInPage.autoSignInUsing(process.env.bamboo_admin_user, process.env.bamboo_admin_password);
    });

    beforeEach(async () => {
        await navBarPage.clickOnCourseAdminMenu();
    });

    it('should load course list', async () => {
        const expect1 = 'arTeMiSApp.course.home.title';
        const value1 = await element(by.id('course-page-heading')).getAttribute('jhiTranslate');
        expect(value1).to.eq(expect1);
    });

    it('should not create new course with empty input', async () => {
        await coursePage.clickOnCreateNewCourse();

        expect(await newCoursePage.save.getAttribute('disabled')).to.eq('true');
        await newCoursePage.clickCancel();
    });

    it('should not create new course without groups', async () => {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);

        expect(await newCoursePage.save.getAttribute('disabled')).to.eq('true');
        await newCoursePage.clickCancel();
    });

    it('should not create new course without groups', async () => {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);

        expect(await newCoursePage.save.getAttribute('disabled')).to.eq('true');
        await newCoursePage.clickCancel();
    });

    it('should save course with title, short title, student and instructor groups', async () => {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);
        await newCoursePage.setStudentGroupName('tumuser');
        await newCoursePage.setInstructorGroupName('artemis-dev');

        expect(await newCoursePage.save.getAttribute('disabled')).to.be.null;
        await newCoursePage.clickSave();

        /*const alert = await element(by.className('alert-success'));
        browser.driver.wait(element)
        await browser.driver.wait(elementIsVisible(alert));

        const alertText = await alert.element(by.tagName('pre')).getText();
        expect(alertText).to.equal(`A new course was created with title ${courseName}`);

        // Dismiss alert
        await alert.element(by.css('button.close')).click();*/
    });

    it('should show the created course in the list', async () => {
        const rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const lastTitle = await rows.last().element(by.css('td:nth-child(2) > span.bold')).getText();

        expect(lastTitle).contains(courseName);
    });

    it('can be deleted', async () => {
        let rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const numberOfCourses = await rows.count();
        const deleteButton = rows.last().element(by.className('btn-danger'));
        await deleteButton.click();

        const confirmDeleteButton = element(by.tagName('jhi-course-delete-dialog')).element(by.className('btn-danger'))
        await confirmDeleteButton.click();


        rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        expect(await rows.count()).to.equal(numberOfCourses - 1);
    });

    after(async () => {
        await navBarPage.autoSignOut();
    });
});
