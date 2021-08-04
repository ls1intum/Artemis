import { POST } from './../support/constants';
import { CypressExamBuilder } from './../support/requests/CourseManagementRequests';
import dayjs from 'dayjs';
import { artemis } from '../support/ArtemisTesting';
import { generateUUID } from '../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;

// Pageobjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.courseManagement;
const examManagement = artemis.pageobjects.examManagement;
const programmingCreation = artemis.pageobjects.programmingExerciseCreation;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const examTitle = 'exam' + uid;

describe('Exam participation', () => {
    let course: any;

    before(() => {
        cy.login(users.getAdmin());
        courseManagementRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, users.getStudentOne().username);
            const exam = new CypressExamBuilder(course).title(examTitle).build();
            courseManagementRequests.createExam(exam);
        });
    });

    beforeEach(() => {
        cy.login(users.getAdmin(), '/');
    });

    it('Adds an exercise group with a programming exercise', () => {
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(courseName, courseShortName);
        examManagement.getExamRow(examTitle).openExerciseGroups();
        cy.contains('Number of exercise groups: 0').should('be.visible');
        // Create a new exercise group
        cy.get('[jhitranslate="artemisApp.examManagement.exerciseGroup.create"]').click();
        const groupName = 'group 1';
        cy.get('#title').clear().type(groupName);
        cy.get('#isMandatory').should('be.checked');
        cy.intercept({ method: POST, url: `/api/courses/${course.id}/exams/*/exerciseGroups` }).as('createExerciseGroup');
        cy.get('[jhitranslate="entity.action.save"]').click();
        cy.wait('@createExerciseGroup');
        cy.contains('Number of exercise groups: 1').should('be.visible');
        // Add programming exercise
        cy.contains('Add Programming Exercise').click();
        const programmingExerciseTitle = 'programming' + uid;
        programmingCreation.setTitle(programmingExerciseTitle);
        programmingCreation.setShortName(uid);
        programmingCreation.setPackageName('de.test');
        programmingCreation.setPoints(10);
        programmingCreation.checkAllowOnlineEditor();
        programmingCreation.generate().its('response.statusCode').should('eq', 201);
        cy.contains(programmingExerciseTitle).should('be.visible');
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagementRequests.deleteCourse(course.id);
        }
    });
});
