import { Course } from 'app/entities/course.model';

import { courseManagementAPIRequest, courseManagementExercises, quizExerciseCreation, quizExerciseDragAndDropQuiz } from '../../../support/artemis';
import { admin } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

let course: Course;

describe('Quiz Exercise Drop Location Spec', () => {
    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('DnD Quiz drop locations', () => {
        before('Create DND quiz', () => {
            cy.login(admin, '/course-management/' + course.id + '/exercises');
            courseManagementExercises.createQuizExercise();
            quizExerciseCreation.setTitle('Quiz Exercise ' + generateUUID());
            quizExerciseDragAndDropQuiz.createDnDQuiz('DnD Quiz Test');
        });

        it.skip('Checks drop locations', () => {
            let containerBounds: DOMRect;

            quizExerciseDragAndDropQuiz.dragUsingCoordinates(310, 320);
            quizExerciseDragAndDropQuiz.dragUsingCoordinates(730, 500);
            quizExerciseDragAndDropQuiz.dragUsingCoordinates(730, 320);

            quizExerciseDragAndDropQuiz.activateInteractiveMode();

            quizExerciseDragAndDropQuiz.markElementAsInteractive(0, 4);
            quizExerciseDragAndDropQuiz.markElementAsInteractive(1, 3);
            quizExerciseDragAndDropQuiz.markElementAsInteractive(2, 3);
            quizExerciseDragAndDropQuiz.markElementAsInteractive(2, 4);

            quizExerciseDragAndDropQuiz.generateQuizExercise();
            quizExerciseDragAndDropQuiz.waitForQuizExerciseToBeGenerated();

            cy.visit(`/course-management/${course.id}/exercises`);
            quizExerciseDragAndDropQuiz.previewQuiz();
            quizExerciseDragAndDropQuiz.waitForQuizPreviewToLoad();

            cy.get('.click-layer').then(($el) => {
                containerBounds = $el[0].getBoundingClientRect();
            });

            cy.get('.drop-location').then(($els) => {
                const { minX, maxX } = quizExerciseDragAndDropQuiz.getXAxis($els);
                expect(containerBounds.right - maxX).to.be.greaterThan(0);
                expect(minX - containerBounds.left).to.be.greaterThan(0);
            });
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
