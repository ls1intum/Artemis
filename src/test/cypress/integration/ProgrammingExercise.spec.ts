import { artemis } from '../support/ArtemisTesting';
import { beVisible } from '../support/constants';
import { CourseManagementPage } from '../support/pageobjects/CourseManagementPage';
import { NavigationBar } from '../support/pageobjects/NavigationBar';
import { generateUUID } from '../support/utils';

/**
 * Admin account.
 */
const admin = artemis.users.getAdmin();

// Requests
const artemisRequests = artemis.requests;

// PageObjects
let courseManagementPage: CourseManagementPage;
let navigationBar: NavigationBar;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let programmingExerciseName: string;
let programmingExerciseShortName: string;
const packageName = 'de.test';

// Selectors
const fieldTitle = '#field_title';
const shortName = '#field_shortName';
const saveEntity = '#save-entity';
const datepickerButtons = '.owl-dt-container-control-button';

describe('Programming Exercise Management', () => {
    let course: any;

    before(() => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(admin);
        artemisRequests.courseManagement
            .createCourse(courseName, courseShortName)
            .its('body')
            .then((body) => {
                expect(body).property('id').to.be.a('number');
                course = body;
                // Wait for group synchronization
                cy.wait(65000);
            });
    });

    beforeEach(() => {
        courseManagementPage = new CourseManagementPage();
        navigationBar = new NavigationBar();
        registerQueries();
        uid = generateUUID();
        programmingExerciseName = 'Cypress programming exercise ' + uid;
        programmingExerciseShortName = 'cypress' + uid;
    });

    describe('Programming exercise creation', () => {
        let programmingExerciseId: number;

        it('Creates a new programming exercise', function () {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(courseName, courseShortName);
            cy.get('#jh-create-entity').click();
            cy.url().should('include', '/programming-exercises/new');
            cy.log('Filling out programming exercise info...');
            cy.get(fieldTitle).type(programmingExerciseName);
            cy.get(shortName).type(programmingExerciseShortName);
            cy.get('#field_packageName').type(packageName);
            cy.get('[label="artemisApp.exercise.releaseDate"] > :nth-child(1) > .btn').should(beVisible).click();
            cy.get(datepickerButtons).wait(500).eq(1).should(beVisible).click();
            cy.get('.test-schedule-date.ng-pristine > :nth-child(1) > .btn').click();
            cy.get('.owl-dt-control-arrow-button').eq(1).click();
            cy.get('.owl-dt-day-3').eq(2).click();
            cy.get(datepickerButtons).eq(1).should(beVisible).click();
            cy.get('#field_points').type('100');
            cy.get('#field_allowOnlineEditor').check();
            cy.get(saveEntity).click();
            cy.wait('@createProgrammingExerciseQuery')
                .its('response.body')
                .then((body) => {
                    expect(body).property('id').to.be.a('number');
                    programmingExerciseId = body.id;
                });
            cy.url().should('include', '/exercises');
            cy.contains(programmingExerciseName).should(beVisible);
        });

        afterEach(() => {
            if (programmingExerciseId) {
                artemisRequests.courseManagement.deleteProgrammingExercise(programmingExerciseId);
            }
        });
    });

    describe('Programming exercise deletion', () => {
        beforeEach(() => {
            artemisRequests.courseManagement.createProgrammingExercise(course, programmingExerciseName, programmingExerciseShortName, packageName).its('status').should('eq', 201);
        });

        it('Deletes an existing programming exercise', function () {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(courseName, courseShortName);
            cy.get('[deletequestion="artemisApp.programmingExercise.delete.question"]').click();
            // Check all checkboxes to get rid of the git repositories and build plans
            cy.get('.modal-body')
                .find('[type="checkbox"]')
                .each(($el) => {
                    cy.wrap($el).check();
                });
            cy.get('[type="text"], [name="confirmExerciseName"]').type(programmingExerciseName).type('{enter}');
            cy.wait('@deleteProgrammingExerciseQuery');
            cy.contains('No Programming Exercises').should(beVisible);
        });
    });

    after(() => {
        if (!!course) {
            artemisRequests.courseManagement.deleteCourse(course.id).its('status').should('eq', 200);
        }
    });
});

/**
 * Sets all the necessary cypress request hooks.
 */
function registerQueries() {
    cy.intercept('DELETE', '/api/programming-exercises/*').as('deleteProgrammingExerciseQuery');
    cy.intercept('POST', '/api/programming-exercises/setup').as('createProgrammingExerciseQuery');
}
