import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
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

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
