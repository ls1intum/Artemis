import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { convertModelAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest, programmingExerciseEditor } from '../../../support/artemis';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import { ProgrammingLanguage } from '../../../support/constants';

describe('Programming exercise participation', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin, '/');
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertModelAfterMultiPart(response);
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
            const submission = javaBuildErrorSubmission;
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
                programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
            });
        });

        it('Makes a partially successful submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentTwo);
            const submission = javaPartiallySuccessfulSubmission;
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
                programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
            });
        });

        it('Makes a successful submission', () => {
            programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentThree);
            const submission = javaAllSuccessfulSubmission;
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
                programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
            });
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
            const submission = cAllSuccessful;
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
                programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
            });
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
            const submission = pythonAllSuccessful;
            programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
                programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
            });
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
