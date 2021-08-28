import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import { POST } from '../../support/constants';

// Accounts
const admin = artemis.users.getAdmin();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// PageObjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.courseManagement;
const quizCreation = artemis.pageobjects.quizExerciseCreation;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let quizExerciseName: string;

describe('Quiz Exercise Management', () => {
    let course: any;

    before('Set up course', () => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(admin);
        courseManagementRequest.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
        });
    });

    beforeEach('New UID', () => {
        uid = generateUUID();
        quizExerciseName = 'Cypress Quiz ' + uid;
        cy.intercept(POST, '/api/quiz-exercises').as('createQuizExercise');
    });

    after('Delete Course', () => {
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Quiz Exercise Creation', () => {
        let quizExercise: any;

        afterEach('Delete Quiz', () => {
            courseManagementRequest.deleteQuizExercise(quizExercise.id);
        });

        it('Creates a Quiz with Multiple Choice', () => {
            beginQuizCreation();
            quizCreation.addMultipleChoiceQuestion('MC Quiz' + uid);
            quizCreation.saveQuiz();
            cy.wait('@createQuizExercise').then((quizResponse) => {
                quizExercise = quizResponse?.response?.body;
                cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizExercise.id + '/preview');
                cy.contains('MC Quiz' + uid).should('be.visible');
            });
        });

        it('Creates a Quiz with Short Answer', () => {
            beginQuizCreation();
            quizCreation.addShortAnswerQuestion('SA Quiz' + uid);
            quizCreation.saveQuiz();
            cy.wait('@createQuizExercise').then((quizResponse) => {
                quizExercise = quizResponse?.response?.body;
                cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizExercise.id + '/preview');
                cy.contains('SA Quiz' + uid).should('be.visible');
            });
        });
    });

    describe('Quiz Exercise deletion', () => {
        let quizExercise: any;

        beforeEach('Create Quiz Exercise', () => {
            courseManagementRequest.createQuizExercise({ course }, quizExerciseName).then((quizResponse) => {
                quizExercise = quizResponse?.body;
            });
        });

        it('Deletes a Quiz Exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(courseName, courseShortName);
            cy.get('#delete-quiz-' + quizExercise.id).click();
            cy.get('.form-control').type(quizExercise.title);
            cy.intercept('DELETE', '/api/quiz-exercises/*').as('deleteQuizQuery');
            cy.get('.modal-footer').contains('Delete').click();
            cy.wait('@deleteQuizQuery').then((deleteResponse) => {
                expect(deleteResponse?.response?.statusCode).to.eq(200);
            });
        });
    });
});

function beginQuizCreation() {
    cy.login(admin, '/');
    navigationBar.openCourseManagement();
    courseManagement.openExercisesOfCourse(courseName, courseShortName);
    cy.get('#create-quiz-button').should('be.visible').click();
    quizCreation.setTitle(quizExerciseName);
}
