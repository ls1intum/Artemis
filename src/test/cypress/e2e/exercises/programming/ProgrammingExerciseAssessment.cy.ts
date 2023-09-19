import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import {
    courseAssessment,
    courseManagement,
    courseManagementAPIRequest,
    exerciseAPIRequest,
    exerciseAssessment,
    exerciseResult,
    programmingExerciseAssessment,
    programmingExerciseEditor,
    programmingExerciseFeedback,
} from '../../../support/artemis';
import { ProgrammingExerciseAssessmentType } from '../../../support/constants';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

// Common primitives
const tutorFeedback = 'You are missing some classes! The classes, which you implemented look good though.';
const tutorFeedbackPoints = 5;
const tutorCodeFeedback = 'The input parameter should be mentioned in javadoc!';
const tutorCodeFeedbackPoints = -2;
const complaint = "That feedback wasn't very useful!";

describe('Programming exercise assessment', () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;

    before('Creates a programming exercise and makes a student submission', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addTutorToCourse(course, tutor);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
            dueDate = dayjs().add(25, 'seconds');
            assessmentDueDate = dueDate.add(30, 'seconds');
            exerciseAPIRequest
                .createProgrammingExercise({
                    course,
                    recordTestwiseCoverage: false,
                    releaseDate: dayjs(),
                    dueDate: dueDate,
                    assessmentDate: assessmentDueDate,
                    assessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC,
                })
                .then((programmingResponse) => {
                    exercise = programmingResponse.body;
                    cy.login(studentOne);
                    exerciseAPIRequest
                        .startExerciseParticipation(exercise.id!)
                        .its('body.id')
                        .then((participationId) => {
                            exerciseAPIRequest.makeProgrammingExerciseSubmission(participationId);
                            // Wait until the due date is in the past
                            const now = dayjs();
                            if (now.isBefore(dueDate)) {
                                cy.wait(dueDate.diff(now, 'ms'));
                            }
                        });
                });
        });
    });

    it('Assesses the programming exercise submission and verifies it', () => {
        // Asses submission
        cy.login(tutor, '/course-management');
        courseManagement.openAssessmentDashboardOfCourse(course.id!);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        programmingExerciseEditor.openFileWithName(exercise.id!, 'BubbleSort.java');
        programmingExerciseAssessment.provideFeedbackOnCodeLine(9, tutorCodeFeedbackPoints, tutorCodeFeedback);
        programmingExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        programmingExerciseAssessment.submit().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
            // Wait until the assessment due date is over
            const now = dayjs();
            if (now.isBefore(assessmentDueDate)) {
                cy.wait(assessmentDueDate.diff(now, 'ms'));
            }
        });

        // Verify assessment as student
        cy.login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        const totalPoints = tutorFeedbackPoints + tutorCodeFeedbackPoints;
        const percentage = totalPoints * 10;
        exerciseResult.shouldShowExerciseTitle(exercise.title!);
        programmingExerciseFeedback.complain(complaint);
        exerciseResult.clickOpenCodeEditor(exercise.id!);
        programmingExerciseFeedback.shouldShowRepositoryLockedWarning();
        programmingExerciseFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
        programmingExerciseFeedback.shouldShowScore(percentage);
        programmingExerciseFeedback.shouldShowCodeFeedback(exercise.id!, 'BubbleSort.java', tutorCodeFeedback, '-2', programmingExerciseEditor);

        // Accept complaint
        cy.login(instructor, `/course-management/${course.id}/complaints`);
        programmingExerciseAssessment.acceptComplaint('Makes sense', false).then((request: Interception) => {
            expect(request.response!.statusCode).to.equal(200);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
