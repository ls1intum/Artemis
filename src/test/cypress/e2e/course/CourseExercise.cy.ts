import { Course } from '../../../../main/webapp/app/entities/course.model';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { courseExercise, courseManagementRequest } from '../../support/artemis';
import { convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { QuizExercise } from '../../../../main/webapp/app/entities/quiz/quiz-exercise.model';

// Common primitives
let courseName: string;
let courseShortName: string;

describe('Course Exercise', () => {
    let course: Course;
    let courseId: number;

    before('Create course', () => {
        cy.login(admin);
        const uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        courseManagementRequest.createCourse(false, courseName, courseShortName).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseId = course.id!;
        });
    });

    describe('Search Exercise', () => {
        let exercise1: QuizExercise;
        let exercise2: QuizExercise;
        let exercise3: QuizExercise;

        before('Create Exercises', () => {
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 1').then((response) => {
                exercise1 = convertModelAfterMultiPart(response);
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 2').then((response) => {
                exercise2 = convertModelAfterMultiPart(response);
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise 3').then((response) => {
                exercise3 = convertModelAfterMultiPart(response);
            });
        });

        it('should filter exercises based on title', () => {
            cy.visit(`/courses/${course.id}/exercises`);
            cy.get(`#exercise-card-${exercise1.id}`).should('be.visible');
            cy.get(`#exercise-card-${exercise2.id}`).should('be.visible');
            cy.get(`#exercise-card-${exercise3.id}`).should('be.visible');
            courseExercise.search('Course Exercise Quiz');
            cy.get(`#exercise-card-${exercise1.id}`).should('be.visible');
            cy.get(`#exercise-card-${exercise2.id}`).should('be.visible');
            cy.get(`#exercise-card-${exercise3.id}`).should('not.exist');
        });

        after('Delete Exercises', () => {
            courseManagementRequest.deleteQuizExercise(exercise1!.id!);
            courseManagementRequest.deleteQuizExercise(exercise2!.id!);
            courseManagementRequest.deleteQuizExercise(exercise3!.id!);
        });
    });

    after('Delete Course', () => {
        if (courseId) {
            courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
        }
    });
});
