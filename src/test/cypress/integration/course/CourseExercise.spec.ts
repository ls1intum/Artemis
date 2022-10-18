import { artemis } from '../../support/ArtemisTesting';
import { Course } from '../../../../main/webapp/app/entities/course.model';
import multipleChoiceQuizTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';

// Accounts
const admin = artemis.users.getAdmin();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Page Objects
const courseExercisePage = artemis.pageobjects.course.exercise;

describe('Course Exercise', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
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

        it('should filter exercises based on search term', function () {
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
        courseManagementRequest.deleteCourse(course.id!);
    });
});
