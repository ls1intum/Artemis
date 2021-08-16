import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;
const examManagementRequests = artemis.requests.examManagement;

// User management
const users = artemis.users;
const student = users.getStudentOne();

// Pageobjects
const courses = artemis.pageobjects.courses;
const courseOverview = artemis.pageobjects.courseOverview;
const examStartEnd = artemis.pageobjects.examStartEnd;
const examNavigation = artemis.pageobjects.examNavigationBar;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const examTitle = 'exam' + uid;

describe('Exam management', () => {
    let course: any;
    let exam: any;

    before(() => {
        cy.login(users.getAdmin());
        courseManagementRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examManagementRequests.registerStudent(course, exam, student);
                examManagementRequests.addExerciseGroup(course, exam, 'group 1', true).then((groupResponse) => {
                    examManagementRequests.addTextExercise(groupResponse.body, 'Text exercise 1');
                });
                examManagementRequests.generateMissingIndividualExams(course, exam);
                examManagementRequests.prepareExerciseStart(course, exam);
            });
        });
    });

    beforeEach(() => {
        cy.login(student, '/');
    });

    it('Participates as a student in a registered exam', () => {
        courses.openCourse(courseName);
        courseOverview.openExamsTab();
        courseOverview.openExam(examTitle);
        cy.url().should('contain', `/exams/${exam.id}`);
        examStartEnd.setConfirmCheckmark();
        examStartEnd.enterFirstnameLastname();
        examStartEnd.startExam();
        examNavigation.openExerciseAtIndex(0);
        const submissionText = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.';
        artemis.pageobjects.textEditor.typeSubmission(submissionText);
        cy.intercept('PUT', `/api/exercises/*/text-submissions`).as('savedSubmission');
        cy.contains('Save').click();
        cy.wait('@savedSubmission').its('request.body.text').should('eq', submissionText);
        examNavigation.handInEarly();
        cy.get('[jhitranslate="artemisApp.examParticipation.handInEarlyNoticeFirstSentence"]').should('be.visible');
        examStartEnd.setConfirmCheckmark();
        examStartEnd.enterFirstnameLastname();
        examStartEnd.finishExam().its('response.statusCode').should('eq', 200);
        cy.get('.alert').contains('Your exam was submitted successfully.');
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagementRequests.deleteCourse(course.id);
        }
    });
});
