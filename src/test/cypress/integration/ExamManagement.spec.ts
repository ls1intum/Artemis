import { TIME_FORMAT } from './../support/constants';
import { artemis } from '../support/ArtemisTesting';
import { generateUUID } from '../support/utils';
import dayjs from 'dayjs';

// Requests
const artemisRequests = artemis.requests;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const examTitle = 'Cypress exam title';

describe('Exam management', () => {
    let courseId: number;

    before(() => {
        cy.login(artemis.users.getAdmin(), '/');
        artemisRequests.courseManagement.createCourse(courseName, courseShortName).then((response) => {
            courseId = response.body.id;
        });
    });

    it('Create exam', function () {
        const creationPage = artemis.pageobjects.examCreation;
        artemis.pageobjects.navigationBar.openCourseManagement();
        artemis.pageobjects.courseManagement.openExamsOfCourse(courseName, courseShortName);
        cy.contains('Create new Exam').click();
        creationPage.setTitle(examTitle);
        creationPage.setVisibleDate(dayjs());
        creationPage.setStartDate(dayjs().add(1, 'day'));
        creationPage.setEndDate(dayjs().add(2, 'day'));
        creationPage.setNumberOfExercises(4);
        creationPage.setMaxPoints(40);

        creationPage.setStartText('Cypress exam start text');
        creationPage.setEndText('Cypress exam end text');
        creationPage.setConfirmationStartText('Cypress exam confirmation start text');
        creationPage.setConfirmationEndText('Cypress exam confirmation end text');
        creationPage.submit().its('response.statusCode').should('eq', 201);
        cy.contains(examTitle).should('be.visible');
    });

    after(() => {
        if (!!courseId) {
            artemisRequests.courseManagement.deleteCourse(courseId);
        }
    });
});
