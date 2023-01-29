import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import allSuccessful from '../../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partiallySuccessful from '../../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import buildError from '../../../fixtures/programming_exercise_submissions/build_error/submission.json';
import { artemis } from '../../../support/ArtemisTesting';
import {
    ProgrammingExerciseSubmission,
    makeSubmissionAndVerifyResults,
    startParticipationInProgrammingExercise,
} from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// The user management object
const users = artemis.users;

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// PageObjects
const editorPage = artemis.pageobjects.exercise.programming.editor;

describe('Programming exercise participations', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Makes a failing submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, users.getStudentOne());
        makeSubmission(exercise, buildError);
    });

    it('Makes a partially successful submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, users.getStudentTwo());
        makeSubmission(exercise, partiallySuccessful);
    });

    it('Makes a successful submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, users.getStudentThree());
        makeSubmission(exercise, allSuccessful);
    });

    after(() => {
        if (course) {
            cy.login(users.getAdmin());
            courseManagementRequests.deleteCourse(course.id!);
        }
    });

    /**
     * Creates a course and a programming exercise inside that course.
     */
    function setupCourseAndProgrammingExercise() {
        cy.login(users.getAdmin(), '/');
        courseManagementRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, users.getStudentOne());
            courseManagementRequests.addStudentToCourse(course, users.getStudentTwo());
            courseManagementRequests.addStudentToCourse(course, users.getStudentThree());
            courseManagementRequests.createProgrammingExercise({ course }).then((exerciseResponse) => {
                exercise = exerciseResponse.body;
            });
        });
    }

    /**
     * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
     */
    function makeSubmission(exercise: ProgrammingExercise, submission: ProgrammingExerciseSubmission) {
        makeSubmissionAndVerifyResults(exercise.id!, editorPage, exercise.packageName!, submission, () => {
            editorPage.getResultScore().contains(submission.expectedResult).and('be.visible');
        });
    }
});
