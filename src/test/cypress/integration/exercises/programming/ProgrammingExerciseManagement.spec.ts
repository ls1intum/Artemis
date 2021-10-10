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

// Selectors
const datepickerButtons = '.owl-dt-container-control-button';

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
            cy.get('[deletequestion="artemisApp.programmingExercise.delete.question"]').click();
            // Check all checkboxes to get rid of the git repositories and build plans
            cy.get('.modal-body')
                .find('[type="checkbox"]')
                .each(($el) => {
                    cy.wrap($el).check();
                });
            cy.intercept(DELETE, PROGRAMMING_EXERCISE_BASE + '*').as('deleteProgrammingExerciseQuery');
            cy.get('[type="text"], [name="confirmExerciseName"]').type(programmingExercise.title).type('{enter}');
            cy.wait('@deleteProgrammingExerciseQuery');
            cy.contains('No Programming Exercises').should('be.visible');
        });
    });

    describe('Programming exercise creation', () => {
        it('Creates a new programming exercise', function () {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagementPage.openExercisesOfCourse(course.title, course.shortName);
            cy.get('#jh-create-entity').click();
            cy.url().should('include', '/programming-exercises/new');
            cy.log('Filling out programming exercise info...');
            const exerciseTitle = 'Cypress programming exercise ' + generateUUID();
            programmingCreation.setTitle(exerciseTitle);
            programmingCreation.setShortName('cypress' + generateUUID());
            programmingCreation.setPackageName('de.test');

            // Set release and due dates via owl date picker
            cy.get('[label="artemisApp.exercise.releaseDate"] > :nth-child(1) > .btn').click();
            cy.get(datepickerButtons).wait(500).eq(1).click().wait(500);
            cy.get('.test-schedule-date.ng-pristine > :nth-child(1) > .btn').click({ force: true });
            cy.get('.owl-dt-control-arrow-button').eq(1).click();
            cy.get('.owl-dt-day-3').eq(2).click();
            cy.get(datepickerButtons).eq(1).click();

            programmingCreation.setPoints(100);
            programmingCreation.checkAllowOnlineEditor();
            programmingCreation.generate().its('response.statusCode').should('eq', 201);
            cy.url().should('include', '/exercises');
            cy.contains(exerciseTitle).should('be.visible');
        });
    });

    after(() => {
        if (!!course) {
            artemisRequests.courseManagement.deleteCourse(course.id);
        }
    });
});
