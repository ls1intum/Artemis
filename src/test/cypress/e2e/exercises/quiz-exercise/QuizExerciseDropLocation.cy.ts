import { Course } from 'app/entities/course.model';
import { admin } from '../../../support/users';
import { courseManagementRequest, quizExerciseDragAndDropQuiz } from '../../../support/artemis';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

let course: Course;

describe('Quiz Exercise Drop Location Spec', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });

    describe('DnD Quiz drop locations', () => {
        before('Create DND quiz', () => {
            cy.login(admin, '/course-management/' + course.id + '/exercises');
            cy.get('#create-dd-quiz').should('be.visible').click();
            cy.get('#create-apollon-diagram').should('be.visible').click();
            cy.get('#field_title').type('cypress-dd-quiz');
            cy.get('#save-dnd-quiz').click();
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
            cy.get('#generate-quiz-exercise').click();
            cy.get('#generate-quiz-exercise-save').click();
            cy.wait(500);
            cy.visit(`/course-management/${course.id}/exercises`);
            cy.get('#preview-quiz').click();
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
});
