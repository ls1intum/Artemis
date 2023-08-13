import { Interception } from 'cypress/types/net-stubbing';

import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import {
    courseAssessment,
    courseManagement,
    courseManagementAPIRequest,
    exerciseAPIRequest,
    exerciseAssessment,
    exerciseResult,
    textExerciseAssessment,
    textExerciseFeedback,
} from '../../../support/artemis';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const tutorTextFeedback = 'Nice ending of the sentence!';
const tutorTextFeedbackPoints = 2;
const complaint = "That feedback wasn't very useful!";

describe('Text exercise assessment', () => {
    let course: Course;
    let exercise: TextExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addTutorToCourse(course, tutor);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
            exerciseAPIRequest.createTextExercise({ course }).then((textResponse) => {
                exercise = textResponse.body;
                cy.login(studentOne);
                exerciseAPIRequest.startExerciseParticipation(exercise.id!);
                cy.fixture('loremIpsum-short.txt').then((submission) => {
                    exerciseAPIRequest.makeTextExerciseSubmission(exercise.id!, submission);
                });
            });
        });
    });

    it('Assesses the text exercise submission', () => {
        cy.login(tutor, '/course-management');
        courseManagement.openAssessmentDashboardOfCourse(course.id!);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        textExerciseAssessment.getInstructionsRootElement().contains(exercise.title!).should('be.visible');
        textExerciseAssessment.getInstructionsRootElement().contains(exercise.problemStatement!).should('be.visible');
        textExerciseAssessment.getInstructionsRootElement().contains(exercise.exampleSolution!).should('be.visible');
        textExerciseAssessment.getInstructionsRootElement().contains(exercise.gradingInstructions!).should('be.visible');
        // Assert the correct word and character count without relying on translations
        textExerciseAssessment.getWordCountElement().should('contain.text', 16).and('be.visible');
        textExerciseAssessment.getCharacterCountElement().should('contain.text', 83).and('be.visible');
        textExerciseAssessment.provideFeedbackOnTextSection(1, tutorTextFeedbackPoints, tutorTextFeedback);
        textExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        textExerciseAssessment.submit().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
        });
    });

    describe('Feedback', () => {
        it('Student sees feedback after assessment due date and complains', () => {
            cy.login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const totalPoints = tutorFeedbackPoints + tutorTextFeedbackPoints;
            const percentage = totalPoints * 10;
            exerciseResult.shouldShowExerciseTitle(exercise.title!);
            exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            exerciseResult.shouldShowScore(percentage);
            exerciseResult.clickOpenExercise(exercise.id!);
            textExerciseFeedback.shouldShowTextFeedback(1, tutorTextFeedback);
            textExerciseFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
            textExerciseFeedback.shouldShowScore(percentage);
            textExerciseFeedback.complain(complaint);
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${course.id}/complaints`);
            textExerciseAssessment.acceptComplaint('Makes sense', false).its('response.statusCode').should('eq', 200);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
