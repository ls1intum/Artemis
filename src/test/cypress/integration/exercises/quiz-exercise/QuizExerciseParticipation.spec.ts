import { artemis } from '../../../support/ArtemisTesting';
import multipleChoiceQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import shortAnswerQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/shortAnswerQuiz_template.json';

// Accounts
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Page objects
const multipleChoiceQuiz = artemis.pageobjects.quizExercise.multipleChoice;
const shortAnswerQuiz = artemis.pageobjects.quizExercise.shortAnswer;
const quizCreation = artemis.pageobjects.quizExercise.creation;
const dragAndDropQuiz = artemis.pageobjects.quizExercise.dragAndDrop;

// Common primitives
let course: any;
let quizExercise: any;

describe('Quiz Exercise Management', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course.id, student.username);
        });
    });

    afterEach('Delete Quiz', () => {
        cy.login(admin);
        courseManagementRequest.deleteQuizExercise(quizExercise.id);
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Quiz exercise participation', () => {
        beforeEach('Create quiz exercise', () => {
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
            });
        });

        it('Student cannot see hidden quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see a visible quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise.id);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible');
            cy.get('.course-exercise-row').first().find('.btn-primary').click();
            cy.get('.quiz-waiting-for-start-overlay > span').should('contain.text', 'This page will refresh automatically, when the quiz starts.');
        });

        it('Student can participate in MC quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise.id);
            courseManagementRequest.startQuizNow(quizExercise.id);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible');
            cy.get('.course-exercise-row').first().find('.btn-primary').click();
            multipleChoiceQuiz.tickAnswerOption(0);
            multipleChoiceQuiz.tickAnswerOption(2);
            multipleChoiceQuiz.submit();
            cy.get('[jhitranslate="artemisApp.quizExercise.successfullySubmittedText"]').should('be.visible');
        });
    });

    describe('SA quiz participation', () => {
        before('Create SA quiz', () => {
            courseManagementRequest.createQuizExercise({ course }, [shortAnswerQuizTemplate]).then((quizResponse) => {
                quizExercise = quizResponse.body;
                courseManagementRequest.setQuizVisible(quizExercise.id);
                courseManagementRequest.startQuizNow(quizExercise.id);
            });
        });

        it('Student can participate in SA quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title);
            cy.get('[jhi-exercise-action-button]').eq(0).click();
            shortAnswerQuiz.typeAnswer(0, 'give');
            shortAnswerQuiz.typeAnswer(1, 'let');
            shortAnswerQuiz.typeAnswer(2, 'run');
            shortAnswerQuiz.typeAnswer(3, 'desert');
            shortAnswerQuiz.typeAnswer(4, 'cry');
            shortAnswerQuiz.typeAnswer(5, 'goodbye');
            shortAnswerQuiz.submit();
        });
    });

    describe('DnD Quiz participation', () => {
        before('Create DND quiz', () => {
            // TODO: it would be great to create the quiz via request. Once the file upload request works it should be easy
            cy.login(admin, '/course-management/' + course.id + '/exercises');
            cy.get('#create-quiz-button').should('be.visible').click();
            quizCreation.setTitle('Cypress Quiz');
            quizCreation.addDragAndDropQuestion('DnD Quiz');
            quizCreation.saveQuiz().then((quizResponse) => {
                quizExercise = quizResponse.response?.body;
                courseManagementRequest.setQuizVisible(quizExercise.id);
                courseManagementRequest.startQuizNow(quizExercise.id);
            });
        });

        it('Student can participate in DnD Quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title);
            cy.get('[jhi-exercise-action-button]').eq(0).click();
            dragAndDropQuiz.dragItemIntoDragArea();
            dragAndDropQuiz.submit();
        });
    });
});
