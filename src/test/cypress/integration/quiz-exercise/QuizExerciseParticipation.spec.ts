import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';

// Accounts
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Page objects
const multipleChoiceQuiz = artemis.pageobjects.multipleChoiceQuiz;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let quizExerciseName: string;

describe('Quiz Exercise Management', () => {
    let course: any;
    let quizExercise: any;

    before('Set up course', () => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(admin);
        courseManagementRequest.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course.id, student.username);
        });
    });

    beforeEach('New UID', () => {
        uid = generateUUID();
        quizExerciseName = 'Cypress Quiz ' + uid;
        cy.login(admin);
    });

    afterEach('Delete Quiz', () => {
        cy.login(admin);
        courseManagementRequest.deleteQuizExercise(quizExercise);
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Cannot access unreleased exercise', () => {
        beforeEach('Create unreleased exercise', () => {
            courseManagementRequest.createQuizExercise(quizExerciseName, dayjs(), { course }).then((quizResponse) => {
                quizExercise = quizResponse?.body;
            });
        });

        it('Student can not see non visible quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see a visible quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible').click();
            cy.get('.btn').contains('Open quiz').click();
            cy.get('.quiz-waiting-for-start-overlay > span').should('contain.text', 'This page will refresh automatically, when the quiz starts.');
        });

        it('Student can participate in MC quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise);
            courseManagementRequest.startQuizNow(quizExercise);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible').click();
            cy.get('.btn').contains('Start quiz').click();
            multipleChoiceQuiz.tickAnswerOption(0);
            multipleChoiceQuiz.tickAnswerOption(2);
            multipleChoiceQuiz.submit();
            cy.get('.quiz-submitted-overlay > span').should('contain.text', 'Your answers have been successfully submitted!');
        });
    });
});
