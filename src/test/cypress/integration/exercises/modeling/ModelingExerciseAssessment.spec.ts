import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../../support/ArtemisTesting';
import day from 'dayjs/esm';

// pageobjects
const assessmentEditor = artemis.pageobjects.assessment.modeling;
const courseAssessmentDashboard = artemis.pageobjects.assessment.course;
const exerciseAssessmentDashboard = artemis.pageobjects.assessment.exercise;
const exerciseResult = artemis.pageobjects.exercise.result;
const modelingFeedback = artemis.pageobjects.exercise.modeling.feedback;
// requests
const courseManagementRequests = artemis.requests.courseManagement;
// Users
const userManagement = artemis.users;
const admin = userManagement.getAdmin();
const student = userManagement.getStudentOne();
const tutor = userManagement.getTutor();
const instructor = userManagement.getInstructor();

describe('Modeling Exercise Assessment Spec', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    before('Log in as admin and create a course', () => {
        createCourseWithModelingExercise().then(() => {
            makeModelingSubmissionAsStudent();
            updateExerciseDueDate();
        });
    });

    after('Delete test course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id!);
    });

    it('Tutor can assess a submission', () => {
        cy.login(tutor, '/course-management');
        cy.get(`[href="/course-management/${course.id}/assessment-dashboard"]`).click();
        cy.url().should('contain', `/course-management/${course.id}/assessment-dashboard`);
        courseAssessmentDashboard.clickExerciseDashboardButton();
        exerciseAssessmentDashboard.clickHaveReadInstructionsButton();
        exerciseAssessmentDashboard.clickStartNewAssessment();
        cy.get('#assessmentLockedCurrentUser').should('be.visible');
        assessmentEditor.addNewFeedback(1, 'Thanks, good job.');
        assessmentEditor.openAssessmentForComponent(1);
        assessmentEditor.assessComponent(-1, 'False');
        assessmentEditor.clickNextAssessment();
        assessmentEditor.assessComponent(2, 'Good');
        assessmentEditor.clickNextAssessment();
        assessmentEditor.assessComponent(0, 'Unnecessary');
        assessmentEditor.submit();
    });

    describe('Handling complaints', () => {
        before(() => {
            cy.login(admin);
            courseManagementRequests
                .updateModelingExerciseAssessmentDueDate(modelingExercise, day())
                .its('body')
                .then((exercise) => {
                    modelingExercise = exercise;
                });
            cy.login(student, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        });

        it('Student can view the assessment and complain', () => {
            cy.login(student, `/courses/${course.id}/exercises/${modelingExercise.id}`);
            exerciseResult.shouldShowExerciseTitle(modelingExercise.title!);
            exerciseResult.shouldShowScore(20);
            exerciseResult.clickViewSubmission();
            modelingFeedback.shouldShowScore(2, 10, 20);
            modelingFeedback.shouldShowAdditionalFeedback(1, 'Thanks, good job.');
            modelingFeedback.shouldShowComponentFeedback(1, 2, 'Good');
            modelingFeedback.complain('I am not happy with your assessment.');
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${course.id}/complaints`);
            courseAssessmentDashboard.showTheComplaint();
            assessmentEditor.rejectComplaint('You are wrong.');
        });
    });

    function createCourseWithModelingExercise() {
        cy.login(admin);
        return courseManagementRequests.createCourse(true).then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course, student);
            courseManagementRequests.addTutorToCourse(course, tutor);
            courseManagementRequests.addInstructorToCourse(course, instructor);
            courseManagementRequests.createModelingExercise({ course }).then((modelingResponse) => {
                modelingExercise = modelingResponse.body;
            });
        });
    }

    function makeModelingSubmissionAsStudent() {
        cy.login(student);
        courseManagementRequests.startExerciseParticipation(modelingExercise.id!).then((participation) => {
            courseManagementRequests.makeModelingExerciseSubmission(modelingExercise.id!, participation.body);
        });
    }

    function updateExerciseDueDate() {
        cy.login(admin);
        courseManagementRequests.updateModelingExerciseDueDate(modelingExercise, day().add(5, 'seconds'));
    }
});
