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
        artemis.pageobjects.navigationBar.openCourseManagement();
        artemis.pageobjects.courseManagement.openExamsOfCourse(courseName, courseShortName);
        cy.contains('Create new Exam').click();
        cy.get('#title').type(examTitle);
        cy.get('#visibleDate').find('input').clear().type(dayjs().format(TIME_FORMAT), { force: true });
        cy.get('#startDate').find('input').clear().type(dayjs().add(1, 'day').format(TIME_FORMAT), { force: true });
        cy.get('#endDate').find('input').clear().type(dayjs().add(2, 'day').format(TIME_FORMAT), { force: true });
        cy.get('#numberOfExercisesInExam').type('4');
        cy.get('#maxPoints').type('40');

        cy.get('#startText').find('.ace_content').type('Cypress exam start text');
        cy.get('#endText').find('.ace_content').type('Cypress exam end text');
        cy.get('#confirmationStartText').find('.ace_content').type('Cypress exam confirmation start text');
        cy.get('#confirmationEndText').find('.ace_content').type('Cypress exam confirmation end text');

        cy.intercept('POST', '/api/courses/*/exams').as('examCreationQuery');
        cy.get('button[type="submit"]').click();
        cy.wait('@examCreationQuery');
        cy.contains(examTitle).should('be.visible');
    });

    after(() => {
        if (!!courseId) {
            artemisRequests.courseManagement.deleteCourse(courseId);
        }
    });
});
