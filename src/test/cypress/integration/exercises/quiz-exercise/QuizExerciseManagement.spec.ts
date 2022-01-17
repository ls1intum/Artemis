import { Interception } from 'cypress/types/net-stubbing';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../../support/ArtemisTesting';
import { generateUUID } from '../../../support/utils';
import multipleChoiceTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { DELETE } from '../../../support/constants';

// Accounts
const admin = artemis.users.getAdmin();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// PageObjects
const courseManagement = artemis.pageobjects.course.management;
const quizCreation = artemis.pageobjects.exercise.quiz.creation;

// Common primitives
let course: Course;
const quizQuestionTitle = 'Cypress Quiz Exercise';

describe('Quiz Exercise Management', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
        });
    });

    after('Delete Course', () => {
        courseManagementRequest.deleteCourse(course.id!);
    });

    describe('Quiz Exercise Creation', () => {
        beforeEach(() => {
            cy.login(admin, '/course-management/');
            courseManagement.openExercisesOfCourse(course.shortName!);
            cy.get('#create-quiz-button').click();
            quizCreation.setTitle('Cypress Quiz Exercise ' + generateUUID());
        });

        it('Creates a Quiz with Multiple Choice', () => {
            quizCreation.addMultipleChoiceQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation();
        });

        it('Creates a Quiz with Short Answer', () => {
            quizCreation.addShortAnswerQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation();
        });

        // TODO: Fix the drag and drop
        it.skip('Creates a Quiz with Drag and Drop', () => {
            quizCreation.addDragAndDropQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation();
        });
    });

    describe('Quiz Exercise deletion', () => {
        let quizExercise: QuizExercise;

        beforeEach('Create Quiz Exercise', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
            });
        });

        it('Deletes a Quiz Exercise', () => {
            cy.login(admin, '/course-management/');
            courseManagement.openExercisesOfCourse(course.shortName!);
            cy.get('#delete-quiz-' + quizExercise.id).click();
            cy.get('#confirm-exercise-name').type(quizExercise.title!);
            cy.intercept(DELETE, '/api/quiz-exercises/*').as('deleteQuizQuery');
            cy.get('#delete').click();
            cy.wait('@deleteQuizQuery').then((deleteResponse) => {
                expect(deleteResponse?.response?.statusCode).to.eq(200);
            });
        });
    });

    function saveAndVerifyQuizCreation() {
        quizCreation.saveQuiz().then((quizResponse: Interception) => {
            cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizResponse.response!.body.id + '/preview');
            cy.contains(quizQuestionTitle).should('be.visible');
        });
    }
});
