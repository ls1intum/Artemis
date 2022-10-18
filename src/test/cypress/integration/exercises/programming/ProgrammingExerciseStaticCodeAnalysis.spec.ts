import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import scaSubmission from '../../../fixtures/programming_exercise_submissions/static_code_analysis/submission.json';
import { artemis } from '../../../support/ArtemisTesting';
import { makeSubmissionAndVerifyResults, startParticipationInProgrammingExercise } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { parseCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const editorPage = artemis.pageobjects.exercise.programming.editor;
const scaConfig = artemis.pageobjects.exercise.programming.scaConfiguration;
const scaFeedback = artemis.pageobjects.exercise.programming.scaFeedback;

describe('Static code analysis tests', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Configures SCA grading and makes a successful submission with SCA errors', () => {
        configureStaticCodeAnalysisGrading();
        startParticipationInProgrammingExercise(course.id!, exercise.id!, users.getStudentOne());
        makeSuccessfulSubmissionWithScaErrors();
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id!);
        }
    });

    /**
     * Creates a course and a programming exercise inside that course.
     */
    function setupCourseAndProgrammingExercise() {
        cy.login(users.getAdmin());
        courseManagement.createCourse(true).then((response) => {
            course = parseCourseAfterMultiPart(response);
            courseManagement.addStudentToCourse(course, users.getStudentOne());
            courseManagement.createProgrammingExercise({ course }, 50).then((dto) => {
                exercise = dto.body;
            });
        });
    }

    /**
     * Makes a submission, which passes all tests, but has some static code analysis issues.
     */
    function makeSuccessfulSubmissionWithScaErrors() {
        makeSubmissionAndVerifyResults(editorPage, exercise.packageName!, scaSubmission, () => {
            editorPage.getResultScore().contains('50%').and('be.visible');
            editorPage.getResultScore().contains('5 points').click();
            scaFeedback.shouldShowPointChart();
            scaFeedback.shouldShowFeedback(13, '10');
            // We have to verify those static texts here. If we don't verify those messages the only difference between the SCA and normal programming exercise
            // tests is the score, which hardly verifies the SCA functionality
            scaFeedback.shouldShowCodeIssue("Variable 'literal1' must be private and have accessor methods.", '5');
            scaFeedback.shouldShowCodeIssue("Avoid unused private fields such as 'LITERAL_TWO'.", '0.5');
            scaFeedback.shouldShowCodeIssue("de.test.BubbleSort.literal1 isn't final but should be", '2.5');
            scaFeedback.shouldShowCodeIssue('Unread public/protected field: de.test.BubbleSort.literal1', '0.2');
            scaFeedback.closeModal();
        });
    }

    /**
     * Configures every SCA category to affect the grading.
     */
    function configureStaticCodeAnalysisGrading() {
        cy.login(users.getAdmin());
        scaConfig.visit(course.id!, exercise.id!);
        scaConfig.makeEveryScaCategoryInfluenceGrading();
        scaConfig.saveChanges();
    }
});
