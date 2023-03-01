import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import javaAllSuccessful from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaPartiallySuccessful from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import javaBuildError from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { ProgrammingExerciseSubmission } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest, programmingExerciseEditor } from '../../../support/artemis';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import { ProgrammingLanguage } from '../../../support/constants';

describe('Programming exercise participations', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addStudentToCourse(course, studentTwo);
            courseManagementRequest.addStudentToCourse(course, studentThree);
        });
    });

    describe('Java programming exercise', () => {
        let exercise: ProgrammingExercise;

        before('Setup java programming exercise', () => {
            cy.login(admin);
            courseManagementRequest
                .createProgrammingExercise({ course }, undefined, undefined, undefined, undefined, undefined, undefined, ProgrammingLanguage.JAVA, undefined, undefined, undefined)
                .then((exerciseResponse) => {
                    exercise = exerciseResponse.body;
                });
        });

        it('Makes a failing submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
            makeSubmission(exercise, javaBuildError);
        });

        it('Makes a partially successful submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentTwo);
            makeSubmission(exercise, javaPartiallySuccessful);
        });

        it('Makes a successful submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentThree);
            makeSubmission(exercise, javaAllSuccessful);
        });
    });

    describe('C programming exercise', () => {
        let exercise: ProgrammingExercise;

        before('Setup c programming exercise', () => {
            cy.login(admin);
            courseManagementRequest
                .createProgrammingExercise({ course }, undefined, undefined, undefined, undefined, undefined, undefined, ProgrammingLanguage.C, undefined, undefined, undefined)
                .then((exerciseResponse) => {
                    exercise = exerciseResponse.body;
                });
        });

        it('Makes a submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
            makeSubmission(exercise, cAllSuccessful);
        });
    });

    describe('Python programming exercise', () => {
        let exercise: ProgrammingExercise;

        before('Setup python programming exercise', () => {
            cy.login(admin);
            courseManagementRequest
                .createProgrammingExercise(
                    { course },
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    undefined,
                    ProgrammingLanguage.PYTHON,
                    undefined,
                    undefined,
                    undefined,
                )
                .then((exerciseResponse) => {
                    exercise = exerciseResponse.body;
                });
        });

        it('Makes a submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
            makeSubmission(exercise, pythonAllSuccessful);
        });
    });

    after('Delete course', () => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});

/**
 * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
 */
function makeSubmission(exercise: ProgrammingExercise, submission: ProgrammingExerciseSubmission) {
    programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, exercise.packageName!, submission, () => {
        programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
    });
}
