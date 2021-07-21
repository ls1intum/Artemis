import { CypressCredentials } from './../support/users';
import { generateUUID } from '../support/utils';
import allSuccessful from '../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partiallySuccessful from '../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import { artemis } from '../support/ArtemisTesting';
import { ProgrammingExerciseSubmission } from '../support/pageobjects/OnlineEditorPage';

// The user management object
const users = artemis.users;

// Requests
const artemisRequests = artemis.requests;

// PageObjects
const editorPage = artemis.pageobjects.onlineEditor;

// Container for a course dto
let course: any;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const programmingExerciseName = 'Cypress programming exercise ' + uid;
const programmingExerciseShortName = courseShortName;
const exercisePath = '/exercises';
const packageName = 'de.test';

// Selectors
const exerciseRow = '.course-exercise-row';

describe('Programming exercise participations', () => {
    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Makes a partially successful submission', function () {
        startParticipationInProgrammingExercise(users.getStudentOne());
        makePartiallySuccessfulSubmission();
    });

    it('Makes a successful submission', function () {
        startParticipationInProgrammingExercise(users.getStudentTwo());
        makeSuccessfulSubmission();
    });

    it('Makes a failing submission', function () {
        startParticipationInProgrammingExercise(users.getStudentThree());
        makeFailingSubmission();
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            artemisRequests.courseManagement.deleteCourse(course.id);
        }
    });
});

/**
 * Creates a course and a programming exercise inside that course.
 */
function setupCourseAndProgrammingExercise() {
    cy.login(users.getAdmin(), '/');
    artemisRequests.courseManagement.createCourse(courseName, courseShortName).then((response) => {
        course = response.body;
        artemisRequests.courseManagement.addStudentToCourse(course.id, users.getStudentOne().username);
        artemisRequests.courseManagement.addStudentToCourse(course.id, users.getStudentTwo().username);
        artemisRequests.courseManagement.addStudentToCourse(course.id, users.getStudentThree().username);
        //  We sleep to allow bamboo/bitbucket to synchronize the group rights, because the programming exercise creation fails otherwise
        cy.log('Created course. Sleeping before adding a programming exercise...');
        cy.wait(65000);
        artemisRequests.courseManagement.createProgrammingExercise(course, programmingExerciseName, programmingExerciseShortName, packageName);
    });
}

/**
 * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
 */
function makeFailingSubmission() {
    const submission = { files: [{ name: 'BubbleSort.java', path: 'programming_exercise_submissions/build_error/BubbleSort.txt' }] };
    makeSubmissionAndVerifyResults(submission, () => {
        editorPage.getResultPanel().contains('Build Failed').should('be.visible');
        editorPage.getResultPanel().contains('0%').should('be.visible');
        editorPage.getBuildOutput().contains('[ERROR] COMPILATION ERROR').should('be.visible');
        editorPage.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="question"]').should('be.visible');
        });
    });
}

/**
 * Makes a submission, which passes and fails some tests, and asserts the outcome in the UI.
 */
function makePartiallySuccessfulSubmission() {
    makeSubmissionAndVerifyResults(partiallySuccessful, () => {
        editorPage.getResultPanel().contains('46%').should('be.visible');
        editorPage.getResultPanel().contains('6 of 13 passed').should('be.visible');
        editorPage.getBuildOutput().contains('No build results available').should('be.visible');
        editorPage.getInstructionSymbols().each(($el, $index) => {
            if ($index < 3) {
                cy.wrap($el).find('[data-icon="check"]').should('be.visible');
            } else {
                cy.wrap($el).find('[data-icon="times"]').should('be.visible');
            }
        });
    });
}

/**
 * Makes a submission, which passes all tests, and asserts the outcome in the UI.
 */
function makeSuccessfulSubmission() {
    makeSubmissionAndVerifyResults(allSuccessful, () => {
        editorPage.getResultPanel().contains('100%').should('be.visible');
        editorPage.getResultPanel().contains('13 of 13 passed').should('be.visible');
        editorPage.getBuildOutput().contains('No build results available').should('be.visible');
        editorPage.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="check"]').should('be.visible');
        });
    });
}

/**
 * General method for entering, submitting and verifying something in the online editor.
 */
function makeSubmissionAndVerifyResults(submission: ProgrammingExerciseSubmission, verifyOutput: () => void) {
    // We create an empty file so that the file browser does not create an extra subfolder when all files are deleted
    editorPage.createFileInRootPackage('placeholderFile');
    // We delete all existing files, so we can create new files and don't have to delete their already existing content
    editorPage.deleteFile('Client.java');
    editorPage.deleteFile('BubbleSort.java');
    editorPage.deleteFile('MergeSort.java');
    editorPage.typeSubmission(submission, packageName);
    editorPage.submit();
    verifyOutput();
}

/**
 * Starts the participation in the test programming exercise.
 */
function startParticipationInProgrammingExercise(credentials: CypressCredentials) {
    cy.login(credentials, '/');
    cy.url().should('include', '/courses');
    cy.log('Participating in the programming exercise as a student...');
    cy.contains(courseName).parents('.card-header').click();
    cy.url().should('include', exercisePath);
    cy.intercept('POST', '/api/courses/*/exercises/*/participations').as('participateInExerciseQuery');
    cy.get(exerciseRow).contains(programmingExerciseName).should('be.visible');
    cy.get(exerciseRow).find('.start-exercise').click();
    cy.wait('@participateInExerciseQuery');
    cy.intercept('GET', '/api/programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
    cy.get(exerciseRow).find('[buttonicon="folder-open"]').click();
    cy.wait('@initialQuery').wait(2000);
}
