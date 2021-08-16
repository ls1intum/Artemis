import { GET } from './../../support/constants';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';

// Requests
const courseRequests = artemis.requests.courseManagement;
const examRequests = artemis.requests.examManagement;

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
const textExerciseTitle = 'Text exercise 1';
const programmingExerciseTitle = 'Programming exercise';
const programmingShortName = 'short' + uid;
const submissionText = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.';

describe('Exam management', () => {
    let course: any;
    let exam: any;

    before(() => {
        cy.login(users.getAdmin());
        courseRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .maxPoints(20)
                .numberOfExercises(2)
                .build();
            courseRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examRequests.registerStudent(course, exam, student);
                examRequests.addExerciseGroup(course, exam, 'group 1', true).then((groupResponse) => {
                    examRequests.addTextExercise(groupResponse.body, textExerciseTitle);
                });
                examRequests.addExerciseGroup(course, exam, 'group 2', true).then((groupResponse) => {
                    examRequests.addProgrammingExercise(groupResponse.body, programmingExerciseTitle, programmingShortName, 'de.test');
                });
                examRequests.generateMissingIndividualExams(course, exam);
                examRequests.prepareExerciseStart(course, exam);
            });
        });
    });

    it('Participates as a student in a registered exam', () => {
        navigateToExam();
        startParticipation();
        openTextExercise();
        makeTextExerciseSubmission();
        openProgrammingExercise();
        makeProgrammingExerciseSubmission();
        handInEarly();
        verifyFinalPage();
    });

    function navigateToExam() {
        cy.login(student, '/');
        courses.openCourse(courseName);
        courseOverview.openExamsTab();
        courseOverview.openExam(examTitle);
        cy.url().should('contain', `/exams/${exam.id}`);
    }

    function startParticipation() {
        examStartEnd.setConfirmCheckmark();
        examStartEnd.enterFirstnameLastname();
        examStartEnd.startExam();
    }

    function openTextExercise() {
        examNavigation.openExerciseAtIndex(0);
    }

    function makeTextExerciseSubmission() {
        artemis.pageobjects.textEditor.typeSubmission(submissionText);
        cy.intercept('PUT', `/api/exercises/*/text-submissions`).as('savedSubmission');
        cy.contains('Save').click();
        cy.wait('@savedSubmission').its('request.body.text').should('eq', submissionText);
    }

    function openProgrammingExercise() {
        cy.intercept(GET, '/api/repository/*/files').as('getFiles');
        examNavigation.navigateRight();
        cy.wait('@getFiles').its('response.statusCode').should('eq', 200);
    }

    function makeProgrammingExerciseSubmission() {
        // TODO
    }

    function handInEarly() {
        examNavigation.handInEarly();
        cy.get('[jhitranslate="artemisApp.examParticipation.handInEarlyNoticeFirstSentence"]').should('be.visible');
        examStartEnd.setConfirmCheckmark();
        examStartEnd.enterFirstnameLastname();
        examStartEnd.finishExam().its('response.statusCode').should('eq', 200);
    }

    function verifyFinalPage() {
        cy.get('.alert').contains('Your exam was submitted successfully.');
        cy.contains(textExerciseTitle).should('be.visible');
        cy.contains(submissionText).should('be.visible');
    }

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseRequests.deleteCourse(course.id);
        }
    });
});
