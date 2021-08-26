import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import multipleChoiceTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import dayjs from 'dayjs';

// Accounts
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// PageObjects

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let quizExerciseName: string;

describe('Quiz Exercise Management', () => {
    let course: any;
    let quizExercise: any;

    before('Set up course', () => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(admin);
        courseManagementRequest.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequest.addStudentToCourse(course.id, student.username);
        });
    });

    beforeEach('New UID', () => {
        uid = generateUUID();
        quizExerciseName = 'Cypress Quiz ' + uid;
        cy.login(admin);
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Cannot access unreleased exercise', () => {
        beforeEach('Create unreleased exercise', () => {
            const mcQuestion: any = multipleChoiceTemplate;
            mcQuestion.title = 'Cypress MC' + uid;
            courseManagementRequest.createQuizExercise([mcQuestion], quizExerciseName, dayjs().add(1, 'day'), { course }).then((quizResponse) => {
                quizExercise = quizResponse?.body;
            });
        });

        it('Student can not access unreleased quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains('No exercises available for the course.').should('be.visible');
        });
    });

    describe('Can participate in released MC quiz', () => {
        beforeEach('Create unreleased exercise', () => {
            const mcQuestion: any = multipleChoiceTemplate;
            mcQuestion.title = 'Cypress MC' + uid;
            courseManagementRequest.createQuizExercise([mcQuestion], quizExerciseName, dayjs().subtract(1, 'day'), { course }).then((quizResponse) => {
                quizExercise = quizResponse?.body;
            });
        });

        it('Student can start a MC quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible').click();
        });
    });
});
