import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { convertModelAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest } from '../../../support/artemis';
import { admin, studentOne, tutor } from '../../../support/users';

// Common primitives
let course: Course;
let quizExercise: QuizExercise;

const resultSelector = '#submission-result-graded';

describe('Quiz Exercise Assessment', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addTutorToCourse(course, tutor);
        });
    });

    afterEach('Delete Quiz', () => {
        deleteQuiz();
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });

    describe('MC Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
            createQuiz();
        });

        it('Assesses a mc quiz submission automatically', () => {
            cy.login(studentOne);
            courseManagementRequest.startExerciseParticipation(quizExercise.id!);
            courseManagementRequest.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.reloadUntilFound(resultSelector);
            cy.contains('50%').should('be.visible');
        });
    });

    describe('SA Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
            createQuiz(shortAnswerQuizTemplate);
        });

        it('Assesses a sa quiz submission automatically', () => {
            cy.login(studentOne);
            courseManagementRequest.startExerciseParticipation(quizExercise.id!);
            courseManagementRequest.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert']);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.reloadUntilFound(resultSelector);
            cy.contains('66.7%').should('be.visible');
        });
    });
});

function createQuiz(quizQuestions: any = multipleChoiceQuizTemplate) {
    cy.login(admin);
    courseManagementRequest.createQuizExercise({ course }, [quizQuestions], undefined, undefined, 1).then((quizResponse) => {
        quizExercise = convertModelAfterMultiPart(quizResponse);
        courseManagementRequest.setQuizVisible(quizExercise.id!);
        courseManagementRequest.startQuizNow(quizExercise.id!);
    });
}

function deleteQuiz() {
    cy.login(admin);
    courseManagementRequest.deleteQuizExercise(quizExercise.id!);
}
