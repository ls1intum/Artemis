import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import { courseManagementRequest, courseOverview, fileUploadExerciseEditor } from 'src/test/cypress/support/artemis';
import { admin, studentOne } from 'src/test/cypress/support/users';
import { convertModelAfterMultiPart } from 'src/test/cypress/support/requests/CourseManagementRequests';
import { Interception } from 'cypress/types/net-stubbing';

describe('File upload exercise participation', () => {
    let course: Course;
    let exercise: FileUploadExercise;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.createFileUploadExercise({ course }).then((exerciseResponse) => {
                exercise = exerciseResponse.body;
            });
        });
    });

    it('Creates a file upload exercise in the UI', () => {
        cy.login(studentOne, `/courses/${course.id}/exercises`);
        courseOverview.startExercise(exercise.id!);
        courseOverview.openRunningExercise(exercise.id!);

        // Verify the initial state of the text editor
        fileUploadExerciseEditor.shouldShowExerciseTitleInHeader(exercise.title!);
        fileUploadExerciseEditor.shouldShowProblemStatement();

        // Make a submission
        fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
        fileUploadExerciseEditor.submit().then((request: Interception) => {
            expect(request.response!.body.submitted).to.be.true;
            expect(request.response!.statusCode).to.eq(200);
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
