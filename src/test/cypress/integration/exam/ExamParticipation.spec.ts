import { GET, BASE_API } from '../../support/constants';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';
import submission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';

// Requests
const courseRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const student = users.getStudentOne();

// Pageobjects
const courses = artemis.pageobjects.courses;
const courseOverview = artemis.pageobjects.courseOverview;
const examStartEnd = artemis.pageobjects.examStartEnd;
const examNavigation = artemis.pageobjects.examNavigationBar;
const onlineEditor = artemis.pageobjects.onlineEditor;

// Common primitives
const uid = generateUUID();
const examTitle = 'exam' + uid;
const submissionText = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.';
const textExerciseTitle = 'Cypress text exercise';

describe('Exam participation', () => {
    let course: any;
    let exam: any;

    before(() => {
        cy.login(users.getAdmin());
        courseRequests.createCourse().then((response) => {
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
                courseRequests.registerStudentForExam(exam, student);
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createAndAddTextExerciseToExam(groupResponse.body, textExerciseTitle);
                });
                courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
                    courseRequests.createProgrammingExercise({ exerciseGroup: groupResponse.body });
                });
                courseRequests.generateMissingIndividualExams(exam);
                courseRequests.prepareExerciseStartForExam(exam);
            });
        });
    });

    it('Participates as a student in a registered exam', () => {
        startParticipation();
        openTextExercise();
        makeTextExerciseSubmission();
        makeProgrammingExerciseSubmission();
        handInEarly();
        verifyFinalPage();
    });

    function startParticipation() {
        cy.login(student, '/');
        courses.openCourse(course.title);
        courseOverview.openExamsTab();
        courseOverview.openExam(examTitle);
        cy.url().should('contain', `/exams/${exam.id}`);
        examStartEnd.startExam();
    }

    function openTextExercise() {
        examNavigation.openExerciseAtIndex(0);
    }

    function makeTextExerciseSubmission() {
        const textEditor = artemis.pageobjects.textEditor;
        textEditor.typeSubmission(submissionText);
        // Loading the content of the existing files might take some time so we wait for the return of the request here
        cy.intercept(GET, BASE_API + 'repository/*/files').as('getFiles');
        textEditor.saveAndContinue().its('request.body.text').should('eq', submissionText);
        cy.wait('@getFiles').its('response.statusCode').should('eq', 200);
    }

    function makeProgrammingExerciseSubmission() {
        onlineEditor.createFileInRootPackage('placeholderFile');
        onlineEditor.deleteFile('Client.java');
        onlineEditor.deleteFile('BubbleSort.java');
        onlineEditor.deleteFile('MergeSort.java');
        onlineEditor.typeSubmission(submission, 'de.test');
        onlineEditor.submit();
        onlineEditor.getResultPanel().contains('100%').should('be.visible');
        onlineEditor.getResultPanel().contains('13 of 13 passed').should('be.visible');
        onlineEditor.getBuildOutput().contains('No build results available').should('be.visible');
        onlineEditor.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="check"]').should('be.visible');
        });
    }

    function handInEarly() {
        examNavigation.handInEarly();
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
