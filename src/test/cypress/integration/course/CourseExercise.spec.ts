import { artemis } from '../../support/ArtemisTesting';
import { Course } from '../../../../main/webapp/app/entities/course.model';
import multipleChoiceQuizTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { ArtemisRequests } from '../../support/requests/ArtemisRequests';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { generateUUID } from '../../support/utils';

// Accounts
const admin = artemis.users.getAdmin();

// Requests
const artemisRequests = artemis.requests;
const courseManagementRequest = artemis.requests.courseManagement;

// Page Objects
const courseExercisePage = artemis.pageobjects.course.exercise;

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
        artemisRequests.courseManagement.createCourse(false, courseName, courseShortName).then((response) => {
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

        it('should filter exercises based on title', function () {
            cy.visit(`/courses/${course.id}/exercises`);
            cy.get(`#exercise-card-${exercise1.id}`).should('be.visible');
            cy.get(`#exercise-card-${exercise2.id}`).should('be.visible');
            cy.get(`#exercise-card-${exercise3.id}`).should('be.visible');
            courseExercisePage.search('Course Exercise Quiz');
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
        if (!!courseId) {
            artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
        }
    });
});
