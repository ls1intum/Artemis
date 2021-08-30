import { generateUUID } from '../support/utils';
import scaSubmission from '../fixtures/programming_exercise_submissions/static_code_analysis/submission.json';
import { artemis } from '../support/ArtemisTesting';
import { makeSubmissionAndVerifyResults, startParticipationInProgrammingExercise } from '../support/pageobjects/OnlineEditorPage';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const editorPage = artemis.pageobjects.programmingExercise.editor;
const scaConfig = artemis.pageobjects.programmingExercise.scaConfiguration;
const scaFeedback = artemis.pageobjects.programmingExercise.scaFeedback;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const programmingExerciseName = 'Cypress programming exercise ' + uid;
const programmingExerciseShortName = courseShortName;
const packageName = 'de.test';

describe('Static code analysis tests', () => {
    let course: any;
    let exercise: any;

    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Configure every static code analysis category to influence grading', () => {
        cy.login(users.getAdmin());
        scaConfig.visit(course.id, exercise.id);
        scaConfig.makeEveryScaCategoryInfluenceGrading();
        scaConfig.saveChanges();
    });

    it('Makes successful submission with SCA errors', function () {
        startParticipationInProgrammingExercise(courseName, programmingExerciseName, users.getStudentOne());
        makeSuccessfulSubmissionWithScaErrors();
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });

    /**
     * Creates a course and a programming exercise inside that course.
     */
    function setupCourseAndProgrammingExercise() {
        cy.login(users.getAdmin());
        courseManagement.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagement.addStudentToCourse(course.id, users.getStudentOne().username);
            courseManagement
                .createProgrammingExercise(programmingExerciseName, programmingExerciseShortName, packageName, { course }, 50)
                .its('body')
                .then((dto) => {
                    exercise = dto;
                });
        });
    }

    /**
     * Makes a submission, which passes all tests, but has some static code analysis issues.
     */
    function makeSuccessfulSubmissionWithScaErrors() {
        makeSubmissionAndVerifyResults(editorPage, packageName, scaSubmission, () => {
            editorPage.getResultPanel().contains('50%').should('be.visible');
            editorPage.getResultPanel().contains('13 of 13 passed').click();
            scaFeedback.shouldShowPointChart();
            scaFeedback.shouldShowFeedback(13, '10.0');
            scaFeedback.shouldShowCodeIssue("Variable 'literal1' must be private and have accessor methods.", '5.0');
            scaFeedback.shouldShowCodeIssue("Avoid unused private fields such as 'LITERAL_TWO'.", '0.5');
            scaFeedback.shouldShowCodeIssue("de.test.BubbleSort.literal1 isn't final but should be", '2.5');
            scaFeedback.shouldShowCodeIssue('Unread public/protected field: de.test.BubbleSort.literal1', '0.2');
            scaFeedback.closeModal();
        });
    }
});
