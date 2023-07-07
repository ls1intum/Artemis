import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import allSuccessful from '../../../fixtures/exercise/programming/all_successful/submission.json';
import partiallySuccessful from '../../../fixtures/exercise/programming/partially_successful/submission.json';
import buildError from '../../../fixtures/exercise/programming/build_error/submission.json';
import { ProgrammingExerciseSubmission } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest, programmingExerciseEditor } from '../../../support/artemis';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';

describe('Programming exercise participations', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Makes a failing submission', () => {
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
        makeSubmission(exercise, buildError);
    });

    it('Makes a partially successful submission', () => {
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentTwo);
        makeSubmission(exercise, partiallySuccessful);
    });

    it('Makes a successful submission', () => {
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentThree);
        makeSubmission(exercise, allSuccessful);
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
        cy.login(admin, '/');
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addStudentToCourse(course, studentTwo);
            courseManagementRequest.addStudentToCourse(course, studentThree);
            courseManagementRequest.createProgrammingExercise({ course }).then((exerciseResponse) => {
                exercise = exerciseResponse.body;
            });
        });
    }

    /**
     * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
     */
    function makeSubmission(exercise: ProgrammingExercise, submission: ProgrammingExerciseSubmission) {
        programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, exercise.packageName!, submission, () => {
            programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
        });
    }
});
