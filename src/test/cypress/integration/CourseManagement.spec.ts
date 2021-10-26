import { artemis } from '../support/ArtemisTesting';
import { CourseManagementPage } from '../support/pageobjects/course/CourseManagementPage';
import { NavigationBar } from '../support/pageobjects/NavigationBar';
import { ArtemisRequests } from '../support/requests/ArtemisRequests';
import { generateUUID } from '../support/utils';

// Requests
const artemisRequests: ArtemisRequests = new ArtemisRequests();

// PagegObjects
const courseManagementPage: CourseManagementPage = new CourseManagementPage();
const navigationBar: NavigationBar = new NavigationBar();

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;

// Selectors
const fieldTitle = '#field_title';
const shortName = '#field_shortName';
const saveEntity = '#save-entity';
const modalDeleteButton = '.modal-footer > .btn-danger';

describe('Course management', () => {
    beforeEach(() => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(artemis.users.getAdmin(), '/');
    });

    describe('Manual student selection', () => {
        let courseId: number;

        beforeEach(() => {
            artemisRequests.courseManagement
                .createCourse(false, courseName, courseShortName)
                .its('body')
                .then((body) => {
                    expect(body).property('id').to.be.a('number');
                    courseId = body.id;
                });
        });

        it('Adds a student manually to the course', function () {
            const username = artemis.users.getStudentOne().username;
            navigationBar.openCourseManagement();
            courseManagementPage.openStudentOverviewOfCourse(courseId);
            cy.intercept('GET', '/api/users/search*').as('getStudentQuery');
            cy.intercept('POST', '/api/courses/*/students/' + username).as('addStudentQuery');
            cy.get('#typeahead-basic').type(username);
            cy.wait('@getStudentQuery');
            cy.get('#ngb-typeahead-0')
                .contains(new RegExp('\\(' + username + '\\)'))
                .should('be.visible')
                .click();
            cy.wait('@addStudentQuery');
            cy.get('[deletequestion="artemisApp.course.courseGroup.removeFromGroup.modalQuestion"]').should('be.visible');
        });

        after(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });

    describe('Course creation', () => {
        let courseId: number;

        it('Creates a new course', function () {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourseCreation();
            cy.get(fieldTitle).type(courseName);
            cy.get(shortName).type(courseShortName);
            cy.get('#field_testCourse').check();
            cy.get('#field_customizeGroupNamesEnabled').uncheck();
            cy.intercept('POST', '/api/courses').as('createCourseQuery');
            cy.get(saveEntity).click();
            cy.wait('@createCourseQuery')
                .its('response.body')
                .then((body) => {
                    expect(body).property('id').to.be.a('number');
                    courseId = body.id;
                });
            courseManagementPage.getCourseCard(courseName, courseShortName).should('be.visible');
        });

        after(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });

    describe('Course deletion', () => {
        beforeEach(() => {
            artemisRequests.courseManagement.createCourse(false, courseName, courseShortName).its('status').should('eq', 201);
        });

        it('Deletes an existing course', function () {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseName, courseShortName);
            cy.get('.btn-danger').click();
            cy.get(modalDeleteButton).should('be.disabled');
            cy.get('[name="confirmExerciseName"]').type(courseName);
            cy.get(modalDeleteButton).should('not.be.disabled').click();
            cy.contains(courseManagementPage.courseSelector(courseName, courseShortName)).should('not.exist');
        });
    });
});
