import { Interception } from 'cypress/types/net-stubbing';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../../support/utils';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { DELETE } from '../../../support/constants';
import { courseManagement, courseManagementExercises, courseManagementRequest, quizExerciseCreation } from '../../../support/artemis';
import { convertModelAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { admin } from '../../../support/users';

describe('Quiz Exercise Management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('Quiz Exercise Creation', () => {
        beforeEach(() => {
            cy.login(admin, '/course-management/');
            courseManagement.openExercisesOfCourse(course.shortName!);
            courseManagementExercises.createQuizExercise();
            quizExerciseCreation.setTitle('Quiz Exercise ' + generateUUID());
        });

        it('Creates a Quiz with Multiple Choice', () => {
            const title = 'Multiple Choice Quiz';
            quizExerciseCreation.addMultipleChoiceQuestion(title);
            quizExerciseCreation.saveQuiz().then((quizResponse: Interception) => {
                cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizResponse.response!.body.id + '/preview');
                cy.contains(title).should('be.visible');
            });
        });

        it('Creates a Quiz with Short Answer', () => {
            const title = 'Short Answer Quiz';
            quizExerciseCreation.addShortAnswerQuestion(title);
            quizExerciseCreation.saveQuiz().then((quizResponse: Interception) => {
                cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizResponse.response!.body.id + '/preview');
                cy.contains(quizQuestionTitle).should('be.visible');
            });
        });

        // TODO: Fix the drag and drop
        // it.skip('Creates a Quiz with Drag and Drop', () => {
        //     quizExerciseCreation.addDragAndDropQuestion(quizQuestionTitle);
        //     quizExerciseCreation.saveQuiz().then((quizResponse: Interception) => {
        //         cy.visit('/course-management/' + course.id + '/quiz-exercises/' + quizResponse.response!.body.id + '/preview');
        //         cy.contains(quizQuestionTitle).should('be.visible');
        //     });
        // });
    });

    describe('Quiz Exercise deletion', () => {
        let quizExercise: QuizExercise;

        beforeEach('Create Quiz Exercise', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceTemplate]).then((quizResponse) => {
                quizExercise = convertModelAfterMultiPart(quizResponse);
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

    after('Delete course', () => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
