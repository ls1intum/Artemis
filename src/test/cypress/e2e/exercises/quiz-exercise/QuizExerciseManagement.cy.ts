import { Interception } from 'cypress/types/net-stubbing';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../../support/utils';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { DELETE } from '../../../support/constants';
import { courseManagement, courseManagementRequest, quizExerciseCreation } from '../../../support/artemis';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { admin } from '../../../support/users';

// Common primitives
let course: Course;
const quizQuestionTitle = 'Cypress Quiz Exercise';

describe('Quiz Exercise Management', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
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
            quizExerciseCreation.setTitle('Cypress Quiz Exercise ' + generateUUID());
        });

        it('Creates a Quiz with Multiple Choice', () => {
            quizExerciseCreation.addMultipleChoiceQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation();
        });

        it('Creates a Quiz with Short Answer', () => {
            quizExerciseCreation.addShortAnswerQuestion(quizQuestionTitle);
            saveAndVerifyQuizCreation();
        });

        // TODO: Fix the drag and drop
        // it.skip('Creates a Quiz with Drag and Drop', () => {
        //     quizExerciseCreation.addDragAndDropQuestion(quizQuestionTitle);
        //     saveAndVerifyQuizCreation();
        // });
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
        quizExerciseCreation.saveQuiz().then((quizResponse: Interception) => {
            cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizResponse.response!.body.id + '/preview');
            cy.contains(quizQuestionTitle).should('be.visible');
        });
    }
});
