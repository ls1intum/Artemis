import { artemis } from '../../../support/ArtemisTesting';
import multipleChoiceQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import shortAnswerQuizTemplate from '../../../fixtures/quiz_exercise_fixtures/shortAnswerQuiz_template.json';
import { CypressExerciseType } from 'src/test/cypress/support/requests/CourseManagementRequests';

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
const courseOverview = artemis.pageobjects.courseOverview;

// Common primitives
let course: any;
let quizExercise: any;

describe('Quiz Exercise Participation', () => {
    before('Set up course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course.id, student.username);
        });
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
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
            courseManagementRequest.setQuizVisible(quizExercise.id);
            cy.login(student, '/courses/' + course.id);
            courseOverview.openRunningExercise(quizExercise.title, CypressExerciseType.QUIZ);
        });

        it('Student can participate in MC quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise.id);
            courseManagementRequest.startQuizNow(quizExercise.id);
            cy.login(student, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id, CypressExerciseType.QUIZ);
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
                courseManagementRequest.setQuizVisible(quizExercise.id);
                courseManagementRequest.startQuizNow(quizExercise.id);
            });
        });

        it('Student can participate in SA quiz', () => {
            cy.login(student, '/courses/' + course.id);
            courseOverview.startExercise(quizExercise.id, CypressExerciseType.QUIZ);
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
            courseOverview.startExercise(quizExercise.id, CypressExerciseType.QUIZ);
            dragAndDropQuiz.dragItemIntoDragArea();
            dragAndDropQuiz.submit();
        });
    });
});
