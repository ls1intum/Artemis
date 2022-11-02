import { Interception } from 'cypress/types/net-stubbing';
import { COURSE_BASE } from '../support/requests/CourseManagementRequests';
import { BASE_API, GET, POST, PUT } from '../support/constants';
import { artemis } from '../support/ArtemisTesting';
import { CourseManagementPage } from '../support/pageobjects/course/CourseManagementPage';
import { NavigationBar } from '../support/pageobjects/NavigationBar';
import { ArtemisRequests } from '../support/requests/ArtemisRequests';
import { generateUUID } from '../support/utils';
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

    describe('Course icon deletion', () => {
        let courseId: number;

        it('Deletes an existing course icon', () => {
            cy.fixture('course/icon.png', 'base64')
                .then(Cypress.Blob.base64StringToBlob)
                .then((blob) => {
                    const formData = new FormData();
                    formData.append('file', blob, 'icon.png');
                    return cy.formRequest(BASE_API + 'fileUpload', POST, formData).then((formRequestResponse) => {
                        const courseIcon = JSON.parse(formRequestResponse.body).path;
                        artemisRequests.courseManagement
                            .createCourse(false, courseName, courseShortName, day().subtract(2, 'hours'), day().add(2, 'hours'), courseIcon)
                            .then((createCourseResponse) => {
                                courseId = createCourseResponse.body!.id!;
                                cy.visit(`/course-management/${courseId}/edit`).then(() => {
                                    cy.get(`#courseImageInput${courseId}`).should('exist');
                                    cy.get('#delete-course-icon').should('exist');
                                });
                            });
                    });
                });
        });

        afterEach(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });
});
