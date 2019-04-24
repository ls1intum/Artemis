import { NavBarPage, SignInPage } from '../page-objects/jhi-page-objects';
import { browser, by, element, ExpectedConditions as ec } from 'protractor';
import { CoursePage, NewCoursePage } from '../page-objects/entities/course-page-object';
import { QuizExercisePage } from '../page-objects/entities/quiz-exercise-page-object';
import { CreateProgrammingExercisePage } from '../page-objects/entities/programming-exercise-object';
import { errorRoute } from '../../../../main/webapp/app/layouts';

const expect = chai.expect;

var urlChanged = function(url) {
    return function() {
        return browser.getCurrentUrl().then(function(actualUrl) {
            return url != actualUrl;
        });
    };
};

describe('Protractor Demo App', function() {
    let signInPage: SignInPage;
    let navBarPage: NavBarPage;
    let coursePage: CoursePage;
    let newCoursePage: NewCoursePage;
    let createProgrammingExercisePage: CreateProgrammingExercisePage;
    let quizExercisePage: QuizExercisePage;
    let courseId: string;
    let quizId: string;

    let courseName: string;

    before(async function() {
        await browser.get('/');
        navBarPage = new NavBarPage(true);
        signInPage = await navBarPage.getSignInPage();
        coursePage = new CoursePage();
        createProgrammingExercisePage = new CreateProgrammingExercisePage();

        newCoursePage = new NewCoursePage();
        courseName = `Protractor${Date.now()}`;
        await signInPage.autoSignInUsing('test', 'user');

        await navBarPage.clickOnCourseAdminMenu();
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);
        await newCoursePage.setStudentGroupName('tumuser');
        await newCoursePage.setTutorGroupName('artemis-dev');
        await newCoursePage.setInstructorGroupName('ls1instructor');
        await newCoursePage.clickSave();

        browser.wait(ec.urlContains('/course'), 1000).then(result => expect(result).to.be.true);

        const rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        courseId = await rows
            .last()
            .element(by.css('td:nth-child(1) > a'))
            .getText();

        // Sign in with instructor account
        await navBarPage.autoSignOut();
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing('test', 'user');
    });

    beforeEach(async () => {});

    it('create programming exercise', async () => {
        await navBarPage.clickOnCourseAdminMenu();

        const exerciseDropdownButton = element(by.id(`exercises-button-${courseId}`));
        expect(exerciseDropdownButton.isPresent());
        await exerciseDropdownButton.click();

        const createProgrammingExerciseButton = await element(by.id('jh-create-entity'));
        // expect(createQuizButton.isPresent());
        await createProgrammingExerciseButton.click();

        browser.wait(urlChanged(`/${courseId}/programming-exercise/new`), 5000);
        createProgrammingExercisePage.setTitle('New Programming Exercise');
        createProgrammingExercisePage.setShortName('newProgrammingExercise');
        createProgrammingExercisePage.setPackageName('todo.main');
        createProgrammingExercisePage.setMaxScore('2000');

        createProgrammingExercisePage.clickSave();

        browser.wait(ec.urlContains(`${courseId}/quiz-exercise/new`), 1000).then((result: any) => expect(result).to.be.true);
    });

    it('should have a title', async function() {
        expect(browser.getTitle()).equal('Courses');
    });

    after(async function() {
        await navBarPage.autoSignOut();
        signInPage = await navBarPage.getSignInPage();
        await signInPage.autoSignInUsing('test', 'user');

        await navBarPage.clickOnCourseAdminMenu();
        browser.waitForAngularEnabled(true);
        //Delete course

        let rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const numberOfCourses = await rows.count();
        const deleteButton = rows.last().element(by.className('btn-danger'));
        await deleteButton.click();

        const confirmDeleteButton = element(by.tagName('jhi-course-delete-dialog')).element(by.className('btn-danger'));
        await confirmDeleteButton.click();

        rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        expect(await rows.count()).to.equal(numberOfCourses - 1);
        await navBarPage.autoSignOut();
    });
});
