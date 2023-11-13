import { Interception } from 'cypress/types/net-stubbing';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { courseManagement, courseManagementAPIRequest, courseManagementExercises, exerciseAPIRequest, navigationBar, quizExerciseCreation } from '../../../support/artemis';
import { admin } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

describe('Quiz Exercise Management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('Quiz Exercise Creation', () => {
        beforeEach(() => {
            cy.login(admin, '/course-management/');
            courseManagement.openExercisesOfCourse(course.id!);
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
                cy.contains(title).should('be.visible');
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

        before('Create quiz Exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createQuizExercise({ course }, [multipleChoiceTemplate]).then((quizResponse) => {
                quizExercise = convertModelAfterMultiPart(quizResponse);
            });
        });

        it('Deletes a quiz exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.id!);
            courseManagementExercises.deleteQuizExercise(quizExercise);
            courseManagementExercises.getExercise(quizExercise.id!).should('not.exist');
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
