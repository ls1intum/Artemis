import { Course } from 'app/entities/course.model';

import { courseManagementAPIRequest, quizExerciseDragAndDropQuiz } from '../../../support/artemis';
import { admin } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

let course: Course;

describe.skip('Quiz Exercise Drop Location Spec', () => {
    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    describe('DnD Quiz drop locations', () => {
        before('Create DND quiz', () => {
            cy.login(admin, '/course-management/' + course.id + '/exercises');
            quizExerciseDragAndDropQuiz.createDnDQuiz('DnD Quiz Test');
        });

        it('Checks drop locations', () => {
            let containerBounds: DOMRect;

            quizExerciseDragAndDropQuiz.dragUsingCoordinates(310, 320);
            quizExerciseDragAndDropQuiz.dragUsingCoordinates(730, 500);
            quizExerciseDragAndDropQuiz.dragUsingCoordinates(1000, 100);

            quizExerciseDragAndDropQuiz.activateInteractiveMode();

            quizExerciseDragAndDropQuiz.markElementAsInteractive(0, 4);
            quizExerciseDragAndDropQuiz.markElementAsInteractive(1, 3);
            quizExerciseDragAndDropQuiz.markElementAsInteractive(2, 3);
            quizExerciseDragAndDropQuiz.markElementAsInteractive(2, 4);

            quizExerciseDragAndDropQuiz.generateQuizExercise();
            cy.wait(500);

            cy.visit(`/course-management/${course.id}/exercises`);
            quizExerciseDragAndDropQuiz.previewQuiz();
            cy.wait(1000);

            cy.get('.click-layer').then(($el) => {
                containerBounds = $el[0].getBoundingClientRect();
            });

            cy.get('.drop-location').then(($els) => {
                const { minX, maxX } = quizExerciseDragAndDropQuiz.getXAxis($els);
                expect(containerBounds.right - maxX)
                    .to.be.lessThan(17)
                    .and.to.be.greaterThan(15);
                expect(minX - containerBounds.left)
                    .to.be.lessThan(17)
                    .and.to.be.greaterThan(15);
            });
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
