import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import scaSubmission from '../../../fixtures/exercise/programming/static_code_analysis/submission.json';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest, programmingExerciseEditor, programmingExerciseScaFeedback, programmingExercisesScaConfig } from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';

describe('Static code analysis tests', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Configures SCA grading and makes a successful submission with SCA errors', () => {
        configureStaticCodeAnalysisGrading();
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
        makeSuccessfulSubmissionWithScaErrors(exercise.id!);
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });

    /**
     * Creates a course and a programming exercise inside that course.
     */
    function setupCourseAndProgrammingExercise() {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.createProgrammingExercise({ course }, 50).then((dto) => {
                exercise = dto.body;
            });
        });
    }

    /**
     * Makes a submission, which passes all tests, but has some static code analysis issues.
     */
    function makeSuccessfulSubmissionWithScaErrors(exerciseID: number) {
        programmingExerciseEditor.makeSubmissionAndVerifyResults(exerciseID, exercise.packageName!, scaSubmission, () => {
            programmingExerciseEditor.getResultScore().contains(scaSubmission.expectedResult).and('be.visible').click();
            programmingExerciseScaFeedback.shouldShowPointChart();
            // We have to verify those static texts here. If we don't verify those messages the only difference between the SCA and normal programming exercise
            // tests is the score, which hardly verifies the SCA functionality
            programmingExerciseScaFeedback.shouldShowCodeIssue("Variable 'literal1' must be private and have accessor methods.", '5');
            programmingExerciseScaFeedback.shouldShowCodeIssue("Avoid unused private fields such as 'LITERAL_TWO'.", '0.5');
            programmingExerciseScaFeedback.shouldShowCodeIssue("de.test.BubbleSort.literal1 isn't final but should be", '2.5');
            programmingExerciseScaFeedback.shouldShowCodeIssue('Unread public/protected field: de.test.BubbleSort.literal1', '0.2');
            programmingExerciseScaFeedback.closeModal();
        });
    }

    /**
     * Configures every SCA category to affect the grading.
     */
    function configureStaticCodeAnalysisGrading() {
        cy.login(admin);
        programmingExercisesScaConfig.visit(course.id!, exercise.id!);
        programmingExercisesScaConfig.makeEveryScaCategoryInfluenceGrading();
        programmingExercisesScaConfig.saveChanges();
    }
});
