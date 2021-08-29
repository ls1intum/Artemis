import { artemis } from 'src/test/cypress/support/ArtemisTesting';
import { generateUUID } from 'src/test/cypress/support/utils';

// The user management object
const users = artemis.users;
const student = users.getStudentOne();
const tutor = users.getTutor();
const admin = users.getAdmin();

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const coursesPage = artemis.pageobjects.courseManagement;
const courseAssessment = artemis.pageobjects.assessment.course;
const exerciseAssessment = artemis.pageobjects.assessment.exercise;
const textAssessment = artemis.pageobjects.assessment.text;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;

// This is a workaround for uncaught athene errors. When opening a text submission athene throws an uncaught exception, which fails the test
Cypress.on('uncaught:exception', (err, runnable) => {
    return false;
});

describe('Text exercise assessment', () => {
    let course: any;
    let exercise: any;

    before(() => {
        createCourseWithTextExercise().then(() => {
            makeTextSubmissionAsStudent();
            updateExerciseDueDateForAssessment();
        });
    });

    it('Assesses the text exercise submission', () => {
        cy.login(tutor, '/course-management');
        coursesPage.openAssessmentDashboardOfCourseWithId(course.id);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        cy.contains('There are no complaints at the moment').should('be.visible');
        cy.contains('There are no requests at the moment.').should('be.visible');
        exerciseAssessment.clickStartNewAssessment();
        textAssessment.getInstructionsRootElement().contains(exercise.title).should('be.visible');
        textAssessment.getInstructionsRootElement().contains(exercise.problemStatement).should('be.visible');
        textAssessment.getInstructionsRootElement().contains(exercise.sampleSolution).should('be.visible');
        textAssessment.getInstructionsRootElement().contains(exercise.gradingInstructions).should('be.visible');
        cy.contains('Number of words: 100').should('be.visible');
        cy.contains('Number of characters: 591').should('be.visible');
        textAssessment.addNewFeedback(4, 'Try to use some newlines next time!');
        textAssessment.submit();
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });

    function createCourseWithTextExercise() {
        cy.login(admin);
        return courseManagement.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagement.addStudentToCourse(course.id, student.username);
            courseManagement.addTutorToCourse(course, tutor);
            courseManagement.createTextExercise({ course }).then((textResponse) => {
                exercise = textResponse.body;
            });
        });
    }

    function makeTextSubmissionAsStudent() {
        cy.login(student);
        courseManagement.startExerciseParticipation(course.id, exercise.id);
        cy.fixture('loremIpsum.txt').then((submission) => {
            courseManagement.makeTextExerciseSubmission(exercise.id, submission);
        });
    }

    function updateExerciseDueDateForAssessment() {
        cy.login(admin);
        courseManagement.updateTextExerciseDueDate(exercise);
        cy.wait(1000);
    }
});
