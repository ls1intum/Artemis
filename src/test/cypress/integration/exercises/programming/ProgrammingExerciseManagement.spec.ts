import { DELETE } from '../../../support/constants';
import { artemis } from '../../../support/ArtemisTesting';
import { generateUUID } from '../../../support/utils';
import { PROGRAMMING_EXERCISE_BASE } from '../../../support/requests/CourseManagementRequests';

//  Admin account
const admin = artemis.users.getAdmin();

// Requests
const artemisRequests = artemis.requests;

// PageObjects
const courseManagementPage = artemis.pageobjects.courseManagement;
const navigationBar = artemis.pageobjects.navigationBar;
const programmingCreation = artemis.pageobjects.programmingExercise.creation;
const courseExercises = artemis.pageobjects.courseManagementExercises;

describe('Programming Exercise Management', () => {
    let course: any;

    before(() => {
        cy.login(admin);
        artemisRequests.courseManagement
            .createCourse(true)
            .its('body')
            .then((body) => {
                expect(body).property('id').to.be.a('number');
                course = body;
            });
    });

    describe('Programming exercise deletion', () => {
        let programmingExercise: any;

        beforeEach(() => {
            artemisRequests.courseManagement
                .createProgrammingExercise({ course })
                .its('body')
                .then((exercise) => {
                    expect(exercise).to.not.be.null;
                    programmingExercise = exercise;
                });
        });

        it('Deletes an existing programming exercise', function () {
            cy.login(admin, '/').wait(500);
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(course.title, course.shortName);
            cy.get('#delete-exercise').click();
            // Check all checkboxes to get rid of the git repositories and build plans
            cy.get('#additional-check').each(($el) => {
                cy.wrap($el).check();
            });
            cy.intercept(DELETE, PROGRAMMING_EXERCISE_BASE + '*').as('deleteProgrammingExerciseQuery');
            cy.get('#confirm-exercise-name').type(programmingExercise.title).type('{enter}');
            cy.wait('@deleteProgrammingExerciseQuery').its('response.statusCode').should('eq', 200);
            cy.contains(programmingExercise.title).should('not.exist');
        });
    });

    describe('Programming exercise creation', () => {
        it('Creates a new programming exercise', function () {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(course.title, course.shortName);
            courseExercises.clickCreateProgrammingExerciseButton();
            cy.url().should('include', '/programming-exercises/new');
            cy.log('Filling out programming exercise info...');
            const exerciseTitle = 'Cypress programming exercise ' + generateUUID();
            programmingCreation.setTitle(exerciseTitle);
            programmingCreation.setShortName('cypress' + generateUUID());
            programmingCreation.setPackageName('de.test');
            programmingCreation.setPoints(100);
            programmingCreation.checkAllowOnlineEditor();
            programmingCreation.generate().its('response.statusCode').should('eq', 201);
            cy.url().should('include', '/exercises');
            courseExercises.shouldContainExerciseWithName(exerciseTitle);
        });
    });

    after(() => {
        if (!!course) {
            artemisRequests.courseManagement.deleteCourse(course.id);
        }
    });
});
