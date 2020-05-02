import { NavBarPage, SignInPage } from '../page-objects/jhi-page-objects';
import { browser, by, element, ExpectedConditions as ec } from 'protractor';
import { CoursePage, NewCoursePage } from '../page-objects/entities/course-page-object';

const expect = chai.expect;

describe('course', function () {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let coursePage: CoursePage;
    let newCoursePage: NewCoursePage;

    let courseName: string;

    before(async function () {
        await browser.get('/');
        navBarPage = new NavBarPage(true);
        signInPage = await navBarPage.getSignInPage();
        coursePage = new CoursePage();
        newCoursePage = new NewCoursePage();
        courseName = `Protractor${Date.now()}`;
        await signInPage.autoSignInUsing(process.env.bamboo_admin_user, process.env.bamboo_admin_password);
    });

    beforeEach(async function () {
        await navBarPage.clickOnCourseAdminMenu();
    });

    it('should load course list', async function () {
        const expect1 = 'artemisApp.course.home.title';
        const value1 = await element(by.id('course-page-heading')).getAttribute('jhiTranslate');
        expect(value1).to.eq(expect1);
    });

    it('should not create new course with empty input', async function () {
        await coursePage.clickOnCreateNewCourse();

        expect(await newCoursePage.save.getAttribute('disabled')).to.equal('true');
        await newCoursePage.clickCancel();
    });

    it('should not create new course without groups', async function () {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);

        expect(await newCoursePage.save.getAttribute('disabled')).to.equal('true');
        await newCoursePage.clickCancel();
    });

    it('should be able to browse and upload a course icon', async function () {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);
        await newCoursePage.browseCourseIcon();
        await newCoursePage.uploadCourseIcon();

        expect(await element(by.css('.headline jhi-secured-image img'))).to.not.equal(undefined);
        await newCoursePage.clickCancel();
    });

    it('should allow to create new course without tutor group', async function () {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);
        await newCoursePage.setStudentGroupName('tumuser');
        await newCoursePage.setInstructorGroupName('ls1instructor');

        expect(await newCoursePage.save.getAttribute('disabled')).to.equal(null);
        await newCoursePage.clickCancel();
    });

    it('should save course with title, short title, coure icon, student, tutor and instructor groups', async function () {
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);
        await newCoursePage.browseCourseIcon();
        await newCoursePage.uploadCourseIcon();
        await newCoursePage.setStudentGroupName('tumuser');
        await newCoursePage.setTutorGroupName('artemis-dev');
        await newCoursePage.setInstructorGroupName('ls1instructor');

        expect(await newCoursePage.save.getAttribute('disabled')).to.equal(null);
        await newCoursePage.clickSave();

        browser.wait(ec.urlContains('/course'), 5000).then((result: any) => expect(result).to.be.true);
    });

    it('should show the created course in the list', async function () {
        const rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const lastTitle = await rows.last().element(by.css('td:nth-child(2) > span.bold')).getText();

        expect(lastTitle).contains(courseName);
    });

    it('can be deleted', async function () {
        let rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const numberOfCourses = await rows.count();
        const deleteButton = rows.last().element(by.className('btn-danger'));
        await deleteButton.click();

        const confirmDeleteButton = element(by.tagName('jhi-course-delete-dialog')).element(by.className('btn-danger'));
        await confirmDeleteButton.click();

        rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        expect(await rows.count()).to.equal(numberOfCourses - 1);
    });

    after(async function () {
        await navBarPage.autoSignOut();
    });
});
