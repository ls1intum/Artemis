import { artemis } from '../../../support/ArtemisTesting';
import shortAnswerQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/shortAnswerQuiz_template.json';
import multipleChoiceQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';

// Accounts
const admin = artemis.users.getAdmin();
const tutor = artemis.users.getTutor();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Common primitives
let course: any;
let quizExercise: any;

const resultSelector = '#submission-result-graded';

describe('Quiz Exercise Assessment', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course.id, student.username);
            courseManagementRequest.addTutorToCourse(course, tutor);
        });
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
            courseManagementRequest.startExerciseParticipation(quizExercise.id);
            courseManagementRequest.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.reloadUntilFound(resultSelector);
            cy.contains('Score 50%').should('be.visible');
        });
    });

    describe('SA Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
            createQuiz(shortAnswerQuizTemplate);
        });

        it('Assesses a sa quiz submission automatically', () => {
            cy.login(student);
            courseManagementRequest.startExerciseParticipation(quizExercise.id);
            courseManagementRequest.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert']);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.reloadUntilFound(resultSelector);
            cy.contains('Score 66.7%').should('be.visible');
        });
    });
});

function createQuiz(quizQuestions: any = multipleChoiceQuizTemplate) {
    cy.login(admin);
    courseManagementRequest.createQuizExercise({ course }, [quizQuestions], undefined, undefined, 1).then((quizResponse) => {
        quizExercise = quizResponse.body;
        courseManagementRequest.setQuizVisible(quizExercise.id);
        courseManagementRequest.startQuizNow(quizExercise.id);
    });
}

function deleteQuiz() {
    cy.login(admin);
    courseManagementRequest.deleteQuizExercise(quizExercise.id);
}
