import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import shortAnswerQuizTemplate from '../../fixtures/quiz_exercise_fixtures/shortAnswerQuiz_template.json';
import dayjs from 'dayjs';

// Accounts
const admin = artemis.users.getAdmin();
const tutor = artemis.users.getTutor();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let quizExerciseName: string;
let course: any;
let quizExercise: any;

describe('Quiz Exercise Assessment', () => {
    before('Set up course', () => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(admin);
        courseManagementRequest.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course.id, student.username);
            courseManagementRequest.addTutorToCourse(course, tutor);
        });
    });

    beforeEach('New UID', () => {
        uid = generateUUID();
        quizExerciseName = 'Cypress Quiz ' + uid;
    });

    afterEach('Delete Quiz', () => {
        deleteQuiz();
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('MC Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
           createQuiz();
        });

        it('Assesses a mc quiz submission automatically', () => {
            cy.login(student);
            courseManagementRequest.startExerciseParticipation(course.id, quizExercise.id);
            courseManagementRequest.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            cy.wait(5000);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.contains(quizExercise.title);
            cy.contains('Score');
        });
    });

    describe.only('SA Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
            createQuiz([shortAnswerQuizTemplate]);
        });

        it('Assesses a sa quiz submission automatically', () => {
            cy.login(student);
            courseManagementRequest.startExerciseParticipation(course.id, quizExercise.id);
            courseManagementRequest.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert', 'cry', 'goodbye']);
            cy.wait(5000);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.contains(quizExercise.title);
            cy.contains('Score');
        });
    });
});

function createQuiz(quizQuestions?: any, duration = 1) {
    courseManagementRequest.createQuizExercise({course}, 'Quiz', dayjs().subtract(1, 'minute'), duration, quizQuestions).then((quizResponse) => {
        quizExercise = quizResponse.body;
        courseManagementRequest.setQuizVisible(quizExercise.id);
        courseManagementRequest.startQuizNow(quizExercise.id);
    });
}

function deleteQuiz() {
    cy.login(admin);
    courseManagementRequest.deleteQuizExercise(quizExercise.id);
}
