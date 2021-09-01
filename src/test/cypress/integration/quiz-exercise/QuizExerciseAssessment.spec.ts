import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
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

describe('Quiz Exercise Assessment', () => {
    let course: any;

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

    after('Delete Course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id);
    });

    describe('Quiz assessment', () => {
        let quizExercise: any;
        before('Creates a quiz and a submission', () => {
           courseManagementRequest.createQuizExercise({course}, 'Quiz', dayjs().subtract(1, 'hour'), 3).then((quizResponse) => {
               quizExercise = quizResponse.body;
               courseManagementRequest.setQuizVisible(quizExercise.id);
               courseManagementRequest.startQuizNow(quizExercise.id);
           });
        });

        it('Assess a quiz submission', () => {
            cy.login(student);
            courseManagementRequest.startExerciseParticipation(course.id, quizExercise.id);
            courseManagementRequest.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            cy.wait(5000);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.contains(quizExercise.title);
            cy.contains('Score');
        });
    });
});
