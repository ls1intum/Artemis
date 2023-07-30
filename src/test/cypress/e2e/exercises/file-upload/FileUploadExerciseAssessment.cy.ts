import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import {
    courseAssessment,
    courseManagement,
    courseManagementRequest,
    courseOverview,
    exerciseAssessment,
    exerciseResult,
    fileUploadExerciseAssessment,
    fileUploadExerciseEditor,
    fileUploadExerciseFeedback,
} from 'src/test/cypress/support/artemis';
import { admin, instructor, studentOne, tutor } from 'src/test/cypress/support/users';
import { convertModelAfterMultiPart } from 'src/test/cypress/support/requests/CourseManagementRequests';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const complaint = "That feedback wasn't very useful!";

describe('File upload exercise assessment', () => {
    let course: Course;
    let exercise: FileUploadExercise;

    before('Creates a file upload exercise and makes a student submission', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addTutorToCourse(course, tutor);
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.createFileUploadExercise({ course }).then((textResponse) => {
                exercise = textResponse.body;
                cy.login(studentOne, `/courses/${course.id}/exercises`);
                courseOverview.startExercise(exercise.id!);
                courseOverview.openRunningExercise(exercise.id!);
                fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
                fileUploadExerciseEditor.submit();
            });
        });
    });

    it('Assesses the file upload exercise submission', () => {
        cy.login(tutor, '/course-management');
        courseManagement.openAssessmentDashboardOfCourse(course.id!);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        fileUploadExerciseAssessment.getInstructionsRootElement().contains(exercise.title!).should('be.visible');
        fileUploadExerciseAssessment.getInstructionsRootElement().contains(exercise.problemStatement!).should('be.visible');
        fileUploadExerciseAssessment.getInstructionsRootElement().contains(exercise.exampleSolution!).should('be.visible');
        fileUploadExerciseAssessment.getInstructionsRootElement().contains(exercise.gradingInstructions!).should('be.visible');
        fileUploadExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        fileUploadExerciseAssessment.submitFeedback();
    });

    describe('Feedback', () => {
        it('Student sees feedback after assessment due date and complains', () => {
            cy.login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const percentage = tutorFeedbackPoints * 10;
            exerciseResult.shouldShowExerciseTitle(exercise.title!);
            exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            exerciseResult.shouldShowScore(percentage);
            exerciseResult.clickOpenExercise(exercise.id!);
            fileUploadExerciseFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
            fileUploadExerciseFeedback.shouldShowScore(percentage);
            fileUploadExerciseFeedback.complain(complaint);
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${course.id}/complaints`);
            fileUploadExerciseAssessment.acceptComplaint('Makes sense', false).its('response.statusCode').should('eq', 200);
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
