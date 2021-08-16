import { GROUP_SYNCHRONIZATION } from './../support/constants';
import { artemis } from '../support/ArtemisTesting';
import { generateUUID } from '../support/utils';

//  Admin account
const admin = artemis.users.getAdmin();

// Requests
const artemisRequests = artemis.requests;

// PageObjects
const courseManagementPage = artemis.pageobjects.courseManagement;
const navigationBar = artemis.pageobjects.navigationBar;
const programmingCreation = artemis.pageobjects.programmingExerciseCreation;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let programmingExerciseName: string;
let programmingExerciseShortName: string;
const packageName = 'de.test';

// Selectors
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
                cy.wait(GROUP_SYNCHRONIZATION);
            });
    });

    beforeEach(() => {
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
            programmingCreation.setTitle(programmingExerciseName);
            programmingCreation.setShortName(programmingExerciseShortName);
            programmingCreation.setPackageName(packageName);

            // Set release and due dates via owl date picker
            cy.get('[label="artemisApp.exercise.releaseDate"] > :nth-child(1) > .btn').should('be.visible').click();
            cy.get(datepickerButtons).wait(500).eq(1).should('be.visible').click();
            cy.get('.test-schedule-date.ng-pristine > :nth-child(1) > .btn').click();
            cy.get('.owl-dt-control-arrow-button').eq(1).click();
            cy.get('.owl-dt-day-3').eq(2).click();
            cy.get(datepickerButtons).eq(1).should('be.visible').click();

            programmingCreation.setPoints(100);
            programmingCreation.checkAllowOnlineEditor();
            programmingCreation
                .generate()
                .its('response.body')
                .then((body) => {
                    expect(body).property('id').to.be.a('number');
                    programmingExerciseId = body.id;
                });
            cy.url().should('include', '/exercises');
            cy.contains(programmingExerciseName).should('be.visible');
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
            cy.intercept('DELETE', '/api/programming-exercises/*').as('deleteProgrammingExerciseQuery');
            cy.get('[type="text"], [name="confirmExerciseName"]').type(programmingExerciseName).type('{enter}');
            cy.wait('@deleteProgrammingExerciseQuery');
            cy.contains('No Programming Exercises').should('be.visible');
        });
    });

    after(() => {
        if (!!course) {
            artemisRequests.courseManagement.deleteCourse(course.id).its('status').should('eq', 200);
        }
    });
});
