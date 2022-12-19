import { Interception } from 'cypress/types/net-stubbing';
import { COURSE_BASE, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { BASE_API, GET, POST, PUT } from '../../support/constants';
import { artemis } from '../../support/ArtemisTesting';
import { CourseManagementPage } from '../../support/pageobjects/course/CourseManagementPage';
import { NavigationBar } from '../../support/pageobjects/NavigationBar';
import { ArtemisRequests } from '../../support/requests/ArtemisRequests';
import { generateUUID } from '../../support/utils';
import { Course } from 'app/entities/course.model';
import day from 'dayjs/esm';

// Requests
const artemisRequests: ArtemisRequests = new ArtemisRequests();

// PageObjects
const courseManagementPage: CourseManagementPage = new CourseManagementPage();
const navigationBar: NavigationBar = new NavigationBar();

// Common primitives
let courseName: string;
let courseShortName: string;

// Selectors
const modalDeleteButton = '#delete';

describe('Course management', () => {
    beforeEach(() => {
        const uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(artemis.users.getInstructor());
        cy.login(artemis.users.getTutor());
        cy.login(artemis.users.getStudentOne());
        cy.login(artemis.users.getStudentTwo());
        cy.login(artemis.users.getStudentThree());
        cy.login(artemis.users.getAdmin(), '/');
    });

    describe('Manual student selection', () => {
        let course: Course;
        let courseId: number;

        beforeEach(() => {
            artemisRequests.courseManagement.createCourse(false, courseName, courseShortName).then((response) => {
                course = convertCourseAfterMultiPart(response);
                courseId = course.id!;
            });
        });

        it('Adds a student manually to the course', () => {
            const username = artemis.users.getStudentOne().username;
            navigationBar.openCourseManagement();
            courseManagementPage.openStudentOverviewOfCourse(courseId);
            cy.intercept(GET, BASE_API + 'users/search*').as('getStudentQuery');
            cy.intercept(POST, COURSE_BASE + '*/students/' + username).as('addStudentQuery');
            cy.get('#typeahead-basic').type(username);
            cy.wait('@getStudentQuery');
            cy.get('#ngb-typeahead-0')
                .contains(new RegExp('\\(' + username + '\\)'))
                .should('be.visible')
                .click();
            cy.wait('@addStudentQuery');
            cy.get('#registered-students').contains(username).should('be.visible');
        });

        it('Removes a student manually from the course', () => {
            const user = artemis.users.getStudentOne();
            const username = user.username;
            artemisRequests.courseManagement.addStudentToCourse(course, user);
            navigationBar.openCourseManagement();
            courseManagementPage.openStudentOverviewOfCourse(courseId);
            cy.get('#registered-students').contains(username).should('be.visible');
            cy.get('#registered-students button[jhideletebutton]').should('be.visible').click();
            cy.get('.modal #delete').click();
            cy.get('#registered-students').contains(username).should('not.exist');
        });

        after(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });

    describe('Course creation', () => {
        let courseId: number;

        it('Creates a new course', () => {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourseCreation();
            cy.get('#field_title').type(courseName);
            cy.get('#field_shortName').type(courseShortName);
            cy.get('#field_testCourse').check();
            cy.get('#field_customizeGroupNamesEnabled').uncheck();
            cy.intercept(POST, BASE_API + 'admin/courses').as('createCourseQuery');
            cy.get('#save-entity').click();
            cy.wait('@createCourseQuery').then((request: Interception) => {
                courseId = request.response!.body.id!;
                cy.get('#course-detail-info-bar').contains(courseName).should('be.visible');
            });
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

        it('Deletes an existing course', () => {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseShortName);
            cy.get('#delete-course').click();
            cy.get(modalDeleteButton).should('be.disabled');
            cy.get('#confirm-exercise-name').type(courseName);
            cy.get(modalDeleteButton).should('not.be.disabled').click();
            courseManagementPage.getCourseCard(courseShortName).should('not.exist');
        });
    });

    describe('Course icon deletion', () => {
        let course: Course;
        let courseId: number;

        it('Deletes an existing course icon', () => {
            cy.fixture('course/icon.png', 'base64')
                .then(Cypress.Blob.base64StringToBlob)
                .then((blob) => {
                    artemisRequests.courseManagement
                        .createCourse(false, courseName, courseShortName, day().subtract(2, 'hours'), day().add(2, 'hours'), 'icon.png', blob)
                        .then((response) => {
                            course = convertCourseAfterMultiPart(response);
                            courseId = course.id!;
                            cy.intercept(PUT, BASE_API + 'courses/' + courseId).as('updateCourseQuery');
                        });
                });
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseShortName);
            cy.get('#edit-course').click();
            cy.get('#delete-course-icon').click();
            cy.get('#delete-course-icon').should('not.exist');
            cy.get('.no-image').should('exist');
            cy.get('#save-entity').click();
            cy.wait('@updateCourseQuery').then(() => {
                cy.get('#edit-course').click();
                cy.get('#delete-course-icon').should('not.exist');
                cy.get('.no-image').should('exist');
            });
        });

        it('Deletes not existing course icon', () => {
            artemisRequests.courseManagement.createCourse(false, courseName, courseShortName, day().subtract(2, 'hours'), day().add(2, 'hours')).then((response) => {
                course = convertCourseAfterMultiPart(response);
                courseId = course.id!;
            });
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseShortName);
            cy.get('#edit-course').click();
            cy.get('#delete-course-icon').should('not.exist');
            cy.get('.no-image').should('exist');
        });

        afterEach(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });
});
