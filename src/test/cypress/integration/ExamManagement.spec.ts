import { artemis } from '../support/ArtemisTesting';
import { generateUUID } from '../support/utils';

// Requests
let artemisRequests = artemis.requests;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
const examTitle = 'Cypress exam title';

describe('Exam management', () => {
    let courseId: number;

    before(() => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;

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
        cy.get('#visibleDate').find('.btn').click().wait(500);
        cy.get('owl-date-time-container').contains('Set').click();

        cy.get('#startDate').find('.btn').click().wait(500);
        // Open next month
        cy.get('.owl-dt-control-arrow-button').eq(1).click();
        // Select the 17th of the next month
        cy.get('.owl-dt-calendar-body').contains('17').click();
        cy.get('owl-date-time-container').contains('Set').click();

        cy.get('#endDate').find('.btn').click().wait(500);
        // Open next month
        cy.get('.owl-dt-control-arrow-button').eq(1).click();
        // Select the 18th of the next month
        cy.get('.owl-dt-calendar-body').contains('18').click();
        cy.get('owl-date-time-container').contains('Set').click();

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
