import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import scaSubmission from '../../../fixtures/programming_exercise_submissions/static_code_analysis/submission.json';
import { artemis } from '../../../support/ArtemisTesting';
import { makeSubmissionAndVerifyResults, startParticipationInProgrammingExercise } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// Users
const users = artemis.users;
const admin = users.getAdmin();
const studentOne = users.getStudentOne();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

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
        startParticipationInProgrammingExercise(course.id!, exercise.id!, studentOne);
        makeSuccessfulSubmissionWithScaErrors(exercise.id!);
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id!);
        }
    });

    /**
     * Creates a course and a programming exercise inside that course.
     */
    function setupCourseAndProgrammingExercise() {
        cy.login(admin);
        courseManagementRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, studentOne);
            courseManagementRequests.createProgrammingExercise({ course }, 50).then((dto) => {
                exercise = dto.body;
            });
        });
    }

    /**
     * Makes a submission, which passes all tests, but has some static code analysis issues.
     */
    function makeSuccessfulSubmissionWithScaErrors(exerciseID: number) {
        makeSubmissionAndVerifyResults(exerciseID, editorPage, exercise.packageName!, scaSubmission, () => {
            editorPage.getResultScore().contains(scaSubmission.expectedResult).and('be.visible').click();
            scaFeedback.shouldShowPointChart();
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
        cy.login(admin);
        scaConfig.visit(course.id!, exercise.id!);
        scaConfig.makeEveryScaCategoryInfluenceGrading();
        scaConfig.saveChanges();
    }
});
