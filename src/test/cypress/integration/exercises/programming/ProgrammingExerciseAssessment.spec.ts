import { CypressAssessmentType } from '../../../support/requests/CourseManagementRequests';
import { artemis } from 'src/test/cypress/support/ArtemisTesting';

// The user management object
const users = artemis.users;
const student = users.getStudentOne();
const tutor = users.getTutor();
const admin = users.getAdmin();
const instructor = users.getInstructor();

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const coursesPage = artemis.pageobjects.courseManagement;
const courseAssessment = artemis.pageobjects.assessment.course;
const exerciseAssessment = artemis.pageobjects.assessment.exercise;
const programmingAssessment = artemis.pageobjects.assessment.programming;
const exerciseResult = artemis.pageobjects.exerciseResult;
const programmingFeedback = artemis.pageobjects.programmingExercise.feedback;
const onlineEditor = artemis.pageobjects.programmingExercise.editor;

describe('Programming exercise assessment', () => {
    let course: any;
    let exercise: any;
    const tutorFeedback = 'You are missing some classes! The classes, which you implemented look good though.';
    const tutorFeedbackPoints = 5;
    const tutorCodeFeedback = 'The input parameter should be mentioned in javadoc!';
    const tutorCodeFeedbackPoints = -2;
    const complaint = "That feedback wasn't very useful!";

    before('Creates a programming exercise and makes a student submission', () => {
        createCourseWithProgrammingExercise().then(() => {
            makeProgrammingSubmissionAsStudent();
            updateExerciseDueDate();
        });
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });

    it('Assesses the programming exercise submission', () => {
        cy.login(tutor, '/course-management');
        coursesPage.openAssessmentDashboardOfCourseWithId(course.id);
        courseAssessment.checkShowFinishedExercises();
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        cy.contains('There are no complaints at the moment').should('be.visible');
        cy.contains('There are no requests at the moment.').should('be.visible');
        exerciseAssessment.clickStartNewAssessment();
        programmingAssessment.getInstructionsRootElement().contains(exercise.title).should('be.visible');
        programmingAssessment.getInstructionsRootElement().find('[jhitranslate="artemisApp.exerciseAssessmentDashboard.programmingExercise.exampleSolution"]').should('be.visible');
        onlineEditor.openFileWithName('BubbleSort.java');
        programmingAssessment.provideFeedbackOnCodeLine(9, tutorCodeFeedbackPoints, tutorCodeFeedback);
        programmingAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        programmingAssessment.submit().its('response.statusCode').should('eq', 200);
    });

    describe('Feedback', () => {
        before(() => {
            updateExerciseAssessmentDueDate();
            cy.login(student, `/courses/${course.id}/exercises/${exercise.id}`);
        });

        it('Student sees feedback after assessment due date and complains', () => {
            const totalPoints = tutorFeedbackPoints + tutorCodeFeedbackPoints;
            const percentage = totalPoints * 10;
            exerciseResult.shouldShowExerciseTitle(exercise.title);
            exerciseResult.clickOpenCodeEditor();
            programmingFeedback.shouldShowRepositoryLockedWarning();
            programmingFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
            programmingFeedback.shouldShowScore(totalPoints, exercise.maxPoints, percentage);
            programmingFeedback.shouldShowCodeFeedback('BubbleSort.java', tutorCodeFeedback, '-2', onlineEditor);
            // The complaint feature is located on the result screen for programming exercises...
            cy.go('back');
            programmingFeedback.complain(complaint);
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${course.id}/assessment-dashboard`);
            courseAssessment.openComplaints(course.id);
            programmingAssessment.acceptComplaint('Makes sense').its('response.statusCode').should('eq', 200);
        });
    });

    function createCourseWithProgrammingExercise() {
        cy.login(admin);
        return courseManagement.createCourse(true).then((response) => {
            course = response.body;
            courseManagement.addStudentToCourse(course.id, student.username);
            courseManagement.addTutorToCourse(course, tutor);
            courseManagement.addInstructorToCourse(course.id, instructor);
            courseManagement.createProgrammingExercise({ course }, undefined, undefined, undefined, undefined, CypressAssessmentType.SEMI_AUTOMATIC).then((programmingResponse) => {
                exercise = programmingResponse.body;
            });
        });
    }

    function makeProgrammingSubmissionAsStudent() {
        cy.login(student);
        courseManagement
            .startExerciseParticipation(course.id, exercise.id)
            .its('body.id')
            .then((participationId) => {
                courseManagement.makeProgrammingExerciseSubmission(participationId);
            });
    }

    function updateExerciseDueDate() {
        cy.login(admin);
        courseManagement.updateProgrammingExerciseDueDate(exercise);
    }

    function updateExerciseAssessmentDueDate() {
        cy.login(admin);
        courseManagement.updateProgrammingExerciseAssessmentDueDate(exercise);
    }
});
