import { Course } from '../../../../main/webapp/app/entities/course.model';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { courseExercise, courseManagementRequest } from '../../support/artemis';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin } from '../../support/users';
import { generateUUID } from '../../support/utils';

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
            course = convertCourseAfterMultiPart(response);
            courseId = course.id!;
        });
    });

    describe('Search Exercise', () => {
        let exercise1: any;
        let exercise2: any;
        let exercise3: any;

        before('Create Exercises', () => {
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 1').then((response) => {
                exercise1 = response.body;
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 2').then((response) => {
                exercise2 = response.body;
            });
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise 3').then((response) => {
                exercise3 = response.body;
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
            courseManagementRequest.deleteQuizExercise(exercise1.id);
            courseManagementRequest.deleteQuizExercise(exercise2.id);
            courseManagementRequest.deleteQuizExercise(exercise3.id);
        });
    });

    after('Delete Course', () => {
        if (courseId) {
            courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
        }
    });
});
