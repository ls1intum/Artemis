import { NavBarPage, SignInPage } from '../page-objects/jhi-page-objects';
import {browser, by, element, ExpectedConditions as ec} from "protractor";
import { CoursePage, NewCoursePage } from '../page-objects/entities/course-page-object';
import { QuizExercisePage } from '../page-objects/entities/quiz-exercise-page-object';
import { errorRoute } from '../../../../main/webapp/app/layouts';

const expect = chai.expect;

describe('quiz-exercise', () => {
    let navBarPage: NavBarPage;
    let signInPage: SignInPage;
    let coursePage: CoursePage;
    let newCoursePage: NewCoursePage;
    let quizExercisePage: QuizExercisePage;
    let courseId: string;
    let quizId: string;

    let courseName: string;

    before(async () => {
        await browser.get('/');
        navBarPage = new NavBarPage(true);
        signInPage = await navBarPage.getSignInPage();
        coursePage = new CoursePage();
        newCoursePage = new NewCoursePage();
        courseName = `Protractor${Date.now()}`;
        await signInPage.autoSignInUsing(process.env.bamboo_admin_user, process.env.bamboo_admin_password);

        await navBarPage.clickOnCourseAdminMenu();
        await coursePage.clickOnCreateNewCourse();

        await newCoursePage.setTitle(courseName);
        await newCoursePage.setShortName(courseName);
        await newCoursePage.setStudentGroupName('tumuser');
        await newCoursePage.setInstructorGroupName('artemis-dev');
        await newCoursePage.clickSave();

        browser.wait(ec.urlContains('/course'), 1000).then((result) => expect(result).to.be.true);

        //TODO: signout as admin and login as instructor / teaching assistant
    });

    beforeEach(async () => {
    });

    it('navigate into quiz-exercise', async () => {

        courseId = await coursePage.navigateIntoLastCourseQuizzes();

        //TODO: this does not seem to work properly
        browser.wait(ec.urlContains(`${courseId}/quiz-exercise`), 5000).then((result) => expect(result).to.be.true);

    });

    it('create quiz', async () => {
        const createQuizButton = await element(by.id('create-quiz-button'));
        // expect(createQuizButton.isPresent());
        await createQuizButton.click();

        //set title of quiz
        const title = element(by.id('quiz-title'));
        title.sendKeys("test-quiz");

        //set duration of quiz
        const durationMinutes = await element(by.id('quiz-duration-minutes'));
        durationMinutes.clear();
        durationMinutes.sendKeys("0");
        const durationSeconds = await element(by.id('quiz-duration-seconds'));
        durationSeconds.clear();
        durationSeconds.sendKeys("5");

        //add MC question
        const addMcButton = await element(by.id('quiz-add-mc-question'));
        await addMcButton.click();

        // set title of mc question
        const mcQuestionTitle = await element(by.id('mc-question-title'));  //TODO: we need to support multiple questions
        mcQuestionTitle.sendKeys("test-mc");

        //deactivate random order to make the test case deterministic
        const randomOrder = await element(by.css('[for="cbRandomizeOrderMC1"]'));
        await randomOrder.click();

        const quizSaveButton = await element(by.id('quiz-save'));
        expect(quizSaveButton.isPresent());
        await quizSaveButton.click();

        browser.wait(ec.urlContains(`${courseId}/quiz-exercise/new`), 1000).then((result) => expect(result).to.be.true);

        const backButton = await element(by.id('quiz-cancel-back-button'));
        expect(backButton.isPresent());
        //TODO: check that the button name is "Back"
        await backButton.click();

        //TODO: check that we leave the page and there is a new entry
    });

    it('participate in quiz', async () => {
        const quizRows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        quizId = await quizRows.last().element(by.css('td:nth-child(1) > a')).getText();

        //set visible
        const setVisibleButton = await element(by.id(`quiz-set-visible-${quizId}`));
        expect(setVisibleButton.isPresent());
        await setVisibleButton.click();

        await browser.sleep(500);  // let's wait shortly so that the server gets everything right with the database

        //start quiz
        const startQuizInstructorButton = await element(by.id(`instructor-quiz-start-${quizId}`));
        expect(startQuizInstructorButton.isPresent());
        await startQuizInstructorButton.click();

        await browser.sleep(500);  // let's wait shortly so that the server gets everything right with the database
        //navigate to courses
        await navBarPage.clickOnCoursesMenu();

        browser.wait(ec.urlContains(`courses`), 1000).then((result) => expect(result).to.be.true);

        //open or start quiz (depends a bit on the timing)
        let startQuizButton = await element(by.id(`student-quiz-start-${quizId}`));
        if (!startQuizButton.isPresent()) {
            startQuizButton = await element(by.id(`student-quiz-open-${quizId}`));
        }
        expect(startQuizButton.isPresent());
        await startQuizButton.click();

        browser.wait(ec.urlContains(`quiz/${quizId}`), 1000).then((result) => expect(result).to.be.true);

        // deactivate because we use timeouts in the quiz participation and otherwise it would not work
        browser.waitForAngularEnabled(false);

        //answer quiz
        //TODO the answer options are random, search for the correct and incorrect answer option before clicking in it
        const firstAnswerOption = await element(by.id(`answer-option-0`));
        expect(firstAnswerOption.isPresent());
        await firstAnswerOption.click();//select
        await firstAnswerOption.click();//deselect
        await firstAnswerOption.click();//select

        const secondAnswerOption = await element(by.id(`answer-option-1`));
        expect(secondAnswerOption.isPresent());
        await secondAnswerOption.click();//select
        await secondAnswerOption.click();//deselect


        //submit quiz
        const submitQuizButton = await element(by.id(`submit-quiz`));
        expect(submitQuizButton.isPresent());
        await submitQuizButton.click();



        //wait until the quiz has finished
        await browser.wait(ec.visibilityOf(element(by.id('quiz-score'))), 10000).then(async (result) => {
            //first possibility to check this
            element(by.id('quiz-score-result')).getText().then(text => {
               expect(text).equals('1/1 (100 %)');
            });

            //second possibility to check this
            const text = await element(by.id('quiz-score-result')).getText();
            expect(text).equals('1/1 (100 %)');

            element(by.id('answer-option-0-correct')).getText().then(text => {
                expect(text).equals('Correct');
            }).catch(error => {
                fail('first answer option not found as correct');
            });

            element(by.id('answer-option-1-correct')).getText().then(text => {
                expect(text).equals('Correct');
            }).catch(error => {
                fail('second answer option not found as correct');
            });
        });

        await browser.sleep(500);

        browser.waitForAngularEnabled(true);
    });

    it('delete quiz', async () => {
        browser.waitForAngularEnabled(false);
        await navBarPage.clickOnCourseAdminMenu();
        browser.waitForAngularEnabled(true);
        courseId = await coursePage.navigateIntoLastCourseQuizzes();
        //TODO delete quiz
    });

    after(async () => {
        await navBarPage.clickOnCourseAdminMenu();
        browser.waitForAngularEnabled(true);
        //Delete course

        let rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const numberOfCourses = await rows.count();
        const deleteButton = rows.last().element(by.className('btn-danger'));
        await deleteButton.click();

        const confirmDeleteButton = element(by.tagName('jhi-course-delete-dialog')).element(by.className('btn-danger'))
        await confirmDeleteButton.click();

        rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        expect(await rows.count()).to.equal(numberOfCourses - 1);
        await navBarPage.autoSignOut();
    });

});
