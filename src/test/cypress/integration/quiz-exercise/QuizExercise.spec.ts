import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';

// Accounts
const admin = artemis.users.getAdmin();

// Requests
const courseManagementRequest = artemis.requests.courseManagement;

// PageObjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.courseManagement;
const quizCreation = artemis.pageobjects.quizExerciseCreation;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;
let quizExerciseName: string;
let quizExerciseShortName: string;

describe('Quiz Exercise Management', () => {
    let course: any;

    before('Set up course', () => {
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        cy.login(admin);
        courseManagementRequest.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
        });
    });

    beforeEach(('New UID'), () => {
       uid = generateUUID();
       quizExerciseName = 'Cypress Quiz ' + uid;
       quizExerciseShortName = 'cypress' + uid;
    });

    after(('Delete Course'), () => {
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Quiz Exercise Creation', () => {
        let quizId: number;

        afterEach('Delete Quiz', () => {
           // courseManagementRequest.deleteQuizExercise(quizId);
        });

        it('Creates a Quiz with Multiple Choice', () => {
           cy.login(admin, '/');
           navigationBar.openCourseManagement();
           courseManagement.openExercisesOfCourse(courseName, courseShortName);
           cy.get('#create-quiz-button').should('be.visible').click();
           quizCreation.setTitle(quizExerciseName);
           quizCreation.addMCQuestion();
        });
    });
});
