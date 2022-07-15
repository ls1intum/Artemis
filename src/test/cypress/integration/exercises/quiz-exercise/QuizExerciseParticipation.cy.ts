import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../../support/ArtemisTesting';
import multipleChoiceQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import shortAnswerQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/shortAnswerQuiz_template.json';

// Accounts
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Page objects
const multipleChoiceQuiz = artemis.pageobjects.exercise.quiz.multipleChoice;
const shortAnswerQuiz = artemis.pageobjects.exercise.quiz.shortAnswer;
const quizCreation = artemis.pageobjects.exercise.quiz.creation;
const dragAndDropQuiz = artemis.pageobjects.exercise.quiz.dragAndDrop;
const courseOverview = artemis.pageobjects.course.overview;

// Common primitives
let course: Course;
let quizExercise: QuizExercise;

describe('Quiz Exercise Participation', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course, student);
        });
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });

    describe('Quiz exercise participation', () => {
        beforeEach('Create quiz exercise', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
            });
        });

        it('Student cannot see hidden quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see a visible quiz', () => {
            cy.login(admin);
            courseManagementRequest.setQuizVisible(quizExercise.id!);
            cy.login(student, '/courses/' + course.id);
            courseOverview.openRunningExercise(quizExercise.id!);
        });

        it('Student can participate in MC quiz', () => {
            cy.login(admin);
            courseManagementRequest.setQuizVisible(quizExercise.id!);
            courseManagementRequest.startQuizNow(quizExercise.id!);
            cy.login(student, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id!);
            multipleChoiceQuiz.tickAnswerOption(0);
            multipleChoiceQuiz.tickAnswerOption(2);
            multipleChoiceQuiz.submit();
        });
    });

    describe('SA quiz participation', () => {
        before('Create SA quiz', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [shortAnswerQuizTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
                courseManagementRequest.setQuizVisible(quizExercise.id!);
                courseManagementRequest.startQuizNow(quizExercise.id!);
            });
        });

        it('Student can participate in SA quiz', () => {
            const quizQuestionId = quizExercise.quizQuestions![0].id!;
            cy.login(student, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id!);
            shortAnswerQuiz.typeAnswer(0, 1, quizQuestionId, 'give');
            shortAnswerQuiz.typeAnswer(1, 1, quizQuestionId, 'let');
            shortAnswerQuiz.typeAnswer(2, 1, quizQuestionId, 'run');
            shortAnswerQuiz.typeAnswer(2, 3, quizQuestionId, 'desert');
            shortAnswerQuiz.typeAnswer(3, 1, quizQuestionId, 'cry');
            shortAnswerQuiz.typeAnswer(4, 1, quizQuestionId, 'goodbye');
            shortAnswerQuiz.submit();
        });
    });

    // TODO: Fix the drag and drop
    describe.skip('DnD Quiz participation', () => {
        before('Create DND quiz', () => {
            cy.login(admin, '/course-management/' + course.id + '/exercises');
            cy.get('#create-quiz-button').should('be.visible').click();
            quizCreation.setTitle('Cypress Quiz');
            quizCreation.addDragAndDropQuestion('DnD Quiz');
            quizCreation.saveQuiz().then((quizResponse) => {
                quizExercise = quizResponse.response?.body;
                courseManagementRequest.setQuizVisible(quizExercise.id!);
                courseManagementRequest.startQuizNow(quizExercise.id!);
            });
        });

        it('Student can participate in DnD Quiz', () => {
            cy.login(student, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id!);
            dragAndDropQuiz.dragItemIntoDragArea(0);
            dragAndDropQuiz.submit();
        });
    });
});
