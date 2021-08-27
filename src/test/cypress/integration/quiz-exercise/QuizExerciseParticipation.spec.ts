import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';

// Accounts
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

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

    afterEach('Delete Quiz', () => {
        cy.login(admin);
        courseManagementRequest.deleteQuizExercise(quizExercise);
    });

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Cannot access unreleased exercise', () => {
        beforeEach('Create unreleased exercise', () => {
            courseManagementRequest.createQuizExercise(quizExerciseName, dayjs(), { course }).then((quizResponse) => {
                quizExercise = quizResponse?.body;
            });
        });

        it('Student can not see non visible quiz', () => {
            cy.login(student, '/courses/' + course.id);
            cy.contains('No exercises available for the course.').should('be.visible');
        });

        it('Student can see a visible quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible').click();
            cy.get('.btn').contains('Open quiz').click();
        });

        it('Student can start and submit to started quiz', () => {
            courseManagementRequest.setQuizVisible(quizExercise);
            courseManagementRequest.startQuizNow(quizExercise);
            cy.login(student, '/courses/' + course.id);
            cy.contains(quizExercise.title).should('be.visible').click();
            cy.get('.btn').contains('Start quiz').click();
        });
    });
});
