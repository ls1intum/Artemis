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

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;

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
