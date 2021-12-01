import { artemis } from '../../../support/ArtemisTesting';
import { generateUUID } from '../../../support/utils';
import multipleChoiceTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { DELETE } from '../../../support/constants';

// Accounts
const admin = artemis.users.getAdmin();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// PageObjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.courseManagement;
const quizCreation = artemis.pageobjects.quizExercise.creation;

// Common primitives
let uid: string;
let course: any;
const quizQuestionTitle = 'Cypress Quiz Exercise';

describe('Quiz Exercise Management', () => {
    before('Set up course', () => {
        uid = generateUUID();
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
        });
    });

    after('Delete Course', () => {
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Quiz Exercise Creation', () => {
        let quizExercise: any;

        beforeEach(() => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.title, course.shortName);
            cy.get('#create-quiz-button').click();
            quizCreation.setTitle('Cypress Quiz Exercise ' + generateUUID());
        });

        it('Creates a Quiz with Multiple Choice', () => {
            quizCreation.addMultipleChoiceQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation(quizQuestionTitle);
        });

        it('Creates a Quiz with Short Answer', () => {
            quizCreation.addShortAnswerQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation(quizQuestionTitle);
        });

        it('Creates a Quiz with Drag and Drop', () => {
            quizCreation.addDragAndDropQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation(quizQuestionTitle);
        });
    });

    describe('Quiz Exercise deletion', () => {
        let quizExercise: any;

        beforeEach('Create Quiz Exercise', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
            });
        });

        it('Deletes a Quiz Exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.title, course.shortName);
            cy.get('#delete-quiz-' + quizExercise.id).click();
            cy.get('.form-control').type(quizExercise.title);
            cy.intercept(DELETE, '/api/quiz-exercises/*').as('deleteQuizQuery');
            cy.get('.modal-footer').find('.btn-danger').click();
            cy.wait('@deleteQuizQuery').then((deleteResponse) => {
                expect(deleteResponse?.response?.statusCode).to.eq(200);
            });
        });
    });

    function saveAndVerifyQuizCreation(expectedQuizTitle: string) {
        quizCreation.saveQuiz().then((quizResponse: any) => {
            cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizResponse.response.body.id + '/preview');
            cy.contains(expectedQuizTitle).should('be.visible');
        });
    }
});
