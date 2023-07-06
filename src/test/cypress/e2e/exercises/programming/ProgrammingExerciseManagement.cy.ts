import { Interception } from 'cypress/types/net-stubbing';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { DELETE } from '../../../support/constants';
import { courseManagement, courseManagementExercises, courseManagementRequest, navigationBar, programmingExerciseCreation } from '../../../support/artemis';
import { generateUUID } from '../../../support/utils';
import { PROGRAMMING_EXERCISE_BASE, convertModelAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { admin } from '../../../support/users';

describe('Programming Exercise Management', () => {
    let course: Course;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertModelAfterMultiPart(response);
            expect(course).property('id').to.be.a('number');
        });
    });

    describe('Programming exercise deletion', () => {
        let programmingExercise: ProgrammingExercise;

        beforeEach(() => {
            courseManagementRequest
                .createProgrammingExercise({ course })
                .its('body')
                .then((exercise) => {
                    expect(exercise).to.not.be.null;
                    programmingExercise = exercise;
                });
        });

        it('Deletes an existing programming exercise', () => {
            cy.login(admin, '/').wait(500);
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.shortName!);
            cy.get('#delete-exercise').click();
            // Check all checkboxes to get rid of the git repositories and build plans
            cy.get('#additional-check-0').check();
            cy.get('#additional-check-1').check();
            cy.get('#confirm-exercise-name').type(programmingExercise.title!);
            cy.intercept(DELETE, PROGRAMMING_EXERCISE_BASE + '*').as('deleteProgrammingExerciseQuery');
            // For some reason the deletion sometimes fails if we do it immediately
            cy.get('#delete').click();
            cy.wait('@deleteProgrammingExerciseQuery').then((request: any) => {
                expect(request.response.statusCode).to.equal(200);
            });
            cy.contains(programmingExercise.title!).should('not.exist');
        });
    });

    describe('Programming exercise creation', () => {
        it('Creates a new programming exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.shortName!);
            courseManagementExercises.createProgrammingExercise();
            cy.url().should('include', '/programming-exercises/new');
            cy.log('Filling out programming exercise info...');
            const exerciseTitle = 'Cypress programming exercise ' + generateUUID();
            programmingExerciseCreation.setTitle(exerciseTitle);
            programmingExerciseCreation.setShortName('cypress' + generateUUID());
            programmingExerciseCreation.setPackageName('de.test');
            programmingExerciseCreation.setPoints(100);
            programmingExerciseCreation.checkAllowOnlineEditor();
            programmingExerciseCreation.generate().then((request: Interception) => {
                const exercise = request.response!.body;
                cy.get('#exercise-detail-title').should('contain.text', exerciseTitle);
                cy.url().should('include', `/programming-exercises/${exercise.id}`);
            });
        });
    });

    after(() => {
        if (course) {
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
