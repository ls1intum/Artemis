import { Interception } from 'cypress/types/net-stubbing';

import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

import { courseManagementAPIRequest, courseOverview, exerciseAPIRequest, fileUploadExerciseEditor } from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('File upload exercise participation', () => {
    let course: Course;
    let exercise: FileUploadExercise;

    before(() => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            exerciseAPIRequest.createFileUploadExercise({ course }).then((exerciseResponse) => {
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
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
