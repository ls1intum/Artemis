import { artemis } from '../../support/ArtemisTesting';

// Accounts
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// Page objects
const multipleChoiceQuiz = artemis.pageobjects.multipleChoiceQuiz;

describe('Quiz Exercise Management', () => {
    let course: any;
    let quizExercise: any;

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
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }).then((quizResponse) => {
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
            cy.contains(quizExercise.title).should('be.visible').click();
            cy.get('.btn').contains('Open quiz').click();
            cy.get('.quiz-waiting-for-start-overlay > span').should('contain.text', 'This page will refresh automatically, when the quiz starts.');
        });

        it('Student can participate in MC quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise.id);
            courseManagementRequest.startQuizNow(quizExercise.id);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible').click();
            cy.get('.btn').contains('Start quiz').click();
            multipleChoiceQuiz.tickAnswerOption(0);
            multipleChoiceQuiz.tickAnswerOption(2);
            multipleChoiceQuiz.submit();
            cy.get('[jhitranslate="artemisApp.quizExercise.successfullySubmittedText"]').should('be.visible');
        });
    });
});
