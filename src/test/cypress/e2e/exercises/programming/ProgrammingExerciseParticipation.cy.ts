import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import allSuccessful from '../../../fixtures/exercise/programming/all_successful/submission.json';
import partiallySuccessful from '../../../fixtures/exercise/programming/partially_successful/submission.json';
import buildError from '../../../fixtures/exercise/programming/build_error/submission.json';
import { convertModelAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest, programmingExerciseEditor } from '../../../support/artemis';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';

describe('Programming exercise participation', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    before('Create course', () => {
        cy.login(admin, '/');
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addStudentToCourse(course, studentTwo);
            courseManagementRequest.addStudentToCourse(course, studentThree);
            courseManagementRequest.createProgrammingExercise({ course }).then((exerciseResponse) => {
                exercise = exerciseResponse.body;
            });
        });
    });

    it('Makes a failing submission', () => {
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
        const submission = buildError;
        programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
            programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
        });
    });

    it('Makes a partially successful submission', () => {
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentTwo);
        const submission = partiallySuccessful;
        programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
            programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
        });
    });

    it('Makes a successful submission', () => {
        programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentThree);
        const submission = allSuccessful;
        programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, () => {
            programmingExerciseEditor.getResultScore().contains(submission.expectedResult).and('be.visible');
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
