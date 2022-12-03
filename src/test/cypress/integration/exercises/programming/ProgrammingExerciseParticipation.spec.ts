import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import allSuccessful from '../../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partiallySuccessful from '../../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import { artemis } from '../../../support/ArtemisTesting';
import { makeSubmissionAndVerifyResults, startParticipationInProgrammingExercise } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

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
        makeFailingSubmission();
    });

    it('Makes a partially successful submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, users.getStudentTwo());
        makePartiallySuccessfulSubmission();
    });

    it('Makes a successful submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, users.getStudentThree());
        makeSuccessfulSubmission();
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
        cy.login(users.getAdmin(), '/');
        courseManagement.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagement.addStudentToCourse(course, users.getStudentOne());
            courseManagement.addStudentToCourse(course, users.getStudentTwo());
            courseManagement.addStudentToCourse(course, users.getStudentThree());
            courseManagement.createProgrammingExercise({ course }).then((exerciseResponse) => {
                exercise = exerciseResponse.body;
            });
        });
    }

    /**
     * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
     */
    function makeFailingSubmission() {
        const submission = { files: [{ name: 'BubbleSort.java', path: 'programming_exercise_submissions/build_error/BubbleSort.txt' }] };
        makeSubmissionAndVerifyResults(editorPage, exercise.packageName!, submission, () => {
            editorPage.getResultScore().contains('Build failed').and('be.visible');
            editorPage.getResultScore().contains('0%').and('be.visible');
        });
    }

    /**
     * Makes a submission, which passes and fails some tests, and asserts the outcome in the UI.
     */
    function makePartiallySuccessfulSubmission() {
        makeSubmissionAndVerifyResults(editorPage, exercise.packageName!, partiallySuccessful, () => {
            editorPage.getResultScore().contains('46.2%').and('be.visible');
        });
    }

    /**
     * Makes a submission, which passes all tests, and asserts the outcome in the UI.
     */
    function makeSuccessfulSubmission() {
        makeSubmissionAndVerifyResults(editorPage, exercise.packageName!, allSuccessful, () => {
            editorPage.getResultScore().contains('100%').and('be.visible');
        });
    }
});
