import { GET, BASE_API } from '../../support/constants';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';
import submission from '../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import modelingExerciseTemplate from '../../fixtures/requests/modelingExercise_template.json';

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
const onlineEditor = artemis.pageobjects.onlineEditor;
const modelingEditor = artemis.pageobjects.modelingEditor;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const examTitle = 'exam' + uid;
const textExerciseTitle = 'Text exercise 1';
const programmingExerciseTitle = 'Programming exercise';
const programmingShortName = 'short' + uid;
const submissionText = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.';
const packageName = 'de.test';

describe('Exam participation', () => {
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
                .maxPoints(30)
                .numberOfExercises(3)
                .build();
            courseRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examRequests.registerStudent(course, exam, student);
                examRequests.addExerciseGroup(course, exam, 'group 1', true).then((groupResponse) => {
                    courseRequests.createTextExercise(textExerciseTitle, { exerciseGroup: groupResponse.body });
                });
                examRequests.addExerciseGroup(course, exam, 'group 2', true).then((groupResponse) => {
                    courseRequests.createProgrammingExercise(programmingExerciseTitle, programmingShortName, packageName, { exerciseGroup: groupResponse.body });
                });
                examRequests.addExerciseGroup(course, exam, 'group 3', true).then((groupResponse) => {
                    courseRequests.createModelingExercise(modelingExerciseTemplate, { exerciseGroup: groupResponse.body });
                });
                examRequests.generateMissingIndividualExams(course, exam);
                examRequests.prepareExerciseStart(course, exam);
            });
        });
    });

    it('Participates as a student in a registered exam', () => {
        startParticipation();
        openTextExercise();
        makeTextExerciseSubmission();
        makeProgrammingExerciseSubmission();
        openModelingExercise();
        makeModelingExerciseSubmission();
        handInEarly();
        verifyFinalPage();
    });

    function startParticipation() {
        cy.login(student, '/');
        courses.openCourse(courseName);
        courseOverview.openExamsTab();
        courseOverview.openExam(examTitle);
        cy.url().should('contain', `/exams/${exam.id}`);
        examStartEnd.startExam();
    }

    function openTextExercise() {
        examNavigation.openExerciseAtIndex(0);
    }

    function openModelingExercise() {
        examNavigation.openExerciseAtIndex(2);
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
        onlineEditor.typeSubmission(submission, packageName);
        onlineEditor.submit();
        onlineEditor.getResultPanel().contains('100%').should('be.visible');
        onlineEditor.getResultPanel().contains('13 of 13 passed').should('be.visible');
        onlineEditor.getBuildOutput().contains('No build results available').should('be.visible');
        onlineEditor.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="check"]').should('be.visible');
        });
    }

    function makeModelingExerciseSubmission() {
        modelingEditor.addComponentToModel(1);
        modelingEditor.addComponentToModel(2);
        modelingEditor.addComponentToModel(3);
        cy.intercept('PUT', '/api/exercises/*/modeling-submissions').as('createModelingSubmission');
        cy.contains('Save').click();
        cy.wait('@createModelingSubmission');
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
