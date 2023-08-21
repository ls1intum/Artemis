import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

import {
    courseAssessment,
    courseManagement,
    courseManagementAPIRequest,
    exerciseAPIRequest,
    exerciseAssessment,
    exerciseResult,
    modelingExerciseAssessment,
    modelingExerciseFeedback,
} from '../../../support/artemis';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('Modeling Exercise Assessment', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addTutorToCourse(course, tutor);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
            exerciseAPIRequest.createModelingExercise({ course }).then((modelingResponse) => {
                modelingExercise = modelingResponse.body;
                cy.login(studentOne);
                cy.wait(500);
                exerciseAPIRequest.startExerciseParticipation(modelingExercise.id!).then((participation) => {
                    exerciseAPIRequest.makeModelingExerciseSubmission(modelingExercise.id!, participation.body);
                    cy.login(instructor);
                    exerciseAPIRequest.updateModelingExerciseDueDate(modelingExercise, dayjs().add(5, 'seconds'));
                });
            });
        });
    });

    it('Tutor can assess a submission', () => {
        cy.login(tutor, '/course-management');
        courseManagement.openAssessmentDashboardOfCourse(course.id!);
        cy.wait(500);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        exerciseAssessment.getLockedMessage().should('be.visible');
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
            exerciseAPIRequest
                .updateModelingExerciseAssessmentDueDate(modelingExercise, dayjs())
                .its('body')
                .then((exercise) => {
                    modelingExercise = exercise;
                });
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

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
