import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import day from 'dayjs/esm';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseAssessment, courseManagementRequest, exerciseAssessment, exerciseResult, modelingExerciseAssessment, modelingExerciseFeedback } from '../../../support/artemis';
import { admin, instructor, studentOne, tutor } from '../../../support/users';

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
        courseManagementRequest.deleteCourse(course.id!);
    });

    it('Tutor can assess a submission', () => {
        cy.login(tutor, '/course-management');
        cy.get(`[href="/course-management/${course.id}/assessment-dashboard"]`).click();
        cy.url().should('contain', `/course-management/${course.id}/assessment-dashboard`);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        cy.get('#assessmentLockedCurrentUser').should('be.visible');
        modelingExerciseAssessment.addNewFeedback(1, 'Thanks, good job.');
        modelingExerciseAssessment.openAssessmentForComponent(1);
        modelingExerciseAssessment.assessComponent(-1, 'False');
        modelingExerciseAssessment.clickNextAssessment();
        modelingExerciseAssessment.assessComponent(2, 'Good');
        modelingExerciseAssessment.clickNextAssessment();
        modelingExerciseAssessment.assessComponent(0, 'Unnecessary');
        modelingExerciseAssessment.submit();
    });

    describe('Handling complaints', () => {
        before(() => {
            cy.login(admin);
            courseManagementRequest
                .updateModelingExerciseAssessmentDueDate(modelingExercise, day())
                .its('body')
                .then((exercise) => {
                    modelingExercise = exercise;
                });
            cy.login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        });

        it('Student can view the assessment and complain', () => {
            cy.login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
            exerciseResult.shouldShowExerciseTitle(modelingExercise.title!);
            exerciseResult.shouldShowScore(20);
            exerciseResult.clickOpenExercise(modelingExercise.id!);
            modelingExerciseFeedback.shouldShowScore(20);
            modelingExerciseFeedback.shouldShowAdditionalFeedback(1, 'Thanks, good job.');
            modelingExerciseFeedback.shouldShowComponentFeedback(1, 2, 'Good');
            modelingExerciseFeedback.complain('I am not happy with your assessment.');
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${course.id}/complaints`);
            courseAssessment.showTheComplaint();
            modelingExerciseAssessment.rejectComplaint('You are wrong.', false);
        });
    });

    function createCourseWithModelingExercise() {
        cy.login(admin);
        return courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addTutorToCourse(course, tutor);
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.createModelingExercise({ course }).then((modelingResponse) => {
                modelingExercise = modelingResponse.body;
            });
        });
    }

    function makeModelingSubmissionAsStudent() {
        cy.login(studentOne);
        courseManagementRequest.startExerciseParticipation(modelingExercise.id!).then((participation) => {
            courseManagementRequest.makeModelingExerciseSubmission(modelingExercise.id!, participation.body);
        });
    }

    function updateExerciseDueDate() {
        cy.login(admin);
        courseManagementRequest.updateModelingExerciseDueDate(modelingExercise, day().add(5, 'seconds'));
    }
});
