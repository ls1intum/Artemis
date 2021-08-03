import { CypressExamBuilder } from './../support/requests/CourseManagementRequests';
import dayjs from 'dayjs';
import { artemis } from '../support/ArtemisTesting';
import { generateUUID } from '../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// Pageobjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.courseManagement;
const examManagement = artemis.pageobjects.examManagement;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;

describe('Exam management', () => {
    let course: any;
    let examTitle: string;

    before(() => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
        });
    });

    beforeEach(() => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin(), '/');
    });

    it('Creates an exam', function () {
        const creationPage = artemis.pageobjects.examCreation;
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(courseName, courseShortName);

        examManagement.createNewExam();
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
        examManagement.getExamRow(examTitle).should('be.visible');
    });

    describe('Exam deletion', () => {
        beforeEach(() => {
            const exam = new CypressExamBuilder(course).title(examTitle).build();
            courseManagementRequests.createExam(exam);
        });

        it('Deletes an existing exam', () => {
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(courseName, courseShortName);
            examManagement.deleteExam(examTitle);
            examManagement.getExamSelector(examTitle).should('not.exist');
        });
    });

    after(() => {
        if (!!course) {
            courseManagementRequests.deleteCourse(course.id);
        }
    });
});
