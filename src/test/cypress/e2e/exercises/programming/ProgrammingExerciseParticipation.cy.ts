import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { courseManagementAPIRequest, exerciseAPIRequest, programmingExerciseEditor } from '../../../support/artemis';
import { ProgrammingLanguage } from '../../../support/constants';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('Programming exercise participation', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin, '/');
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addStudentToCourse(course, studentTwo);
            courseManagementAPIRequest.addStudentToCourse(course, studentThree);
        });
    });

    describe('Java programming exercise', () => {
        let exercise: ProgrammingExercise;

        before('Setup java programming exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.JAVA }).then((exerciseResponse) => {
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

    // Skip C tests within Jenkins used by the Postgres setup, since C is currently not supported there
    // See https://github.com/ls1intum/Artemis/issues/6994
    if (Cypress.env('DB_TYPE') !== 'Postgres') {
        describe('C programming exercise', () => {
            let exercise: ProgrammingExercise;

            before('Setup c programming exercise', () => {
                cy.login(admin);
                exerciseAPIRequest.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C }).then((exerciseResponse) => {
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
    }

    describe('Python programming exercise', () => {
        let exercise: ProgrammingExercise;

        before('Setup python programming exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.PYTHON }).then((exerciseResponse) => {
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
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
