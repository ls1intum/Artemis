import { Interception } from 'cypress/types/net-stubbing';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../../support/utils';
import dayjs from 'dayjs/esm';
import { courseManagement, courseManagementExercises, courseManagementRequest, fileUploadExerciseCreation, navigationBar } from 'src/test/cypress/support/artemis';
import { admin } from 'src/test/cypress/support/users';
import { convertModelAfterMultiPart } from 'src/test/cypress/support/requests/CourseManagementRequests';

describe('File upload exercise management', () => {
    let course: Course;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
        });
    });

    it('Creates a file upload exercise in the UI', () => {
        cy.visit('/');
        navigationBar.openCourseManagement();
        courseManagement.openExercisesOfCourse(course.id!);
        courseManagementExercises.createFileUploadExercise();

        // Fill out file upload exercise form
        const exerciseTitle = 'file upload exercise' + generateUUID();
        fileUploadExerciseCreation.typeTitle(exerciseTitle);
        fileUploadExerciseCreation.setReleaseDate(dayjs());
        fileUploadExerciseCreation.setDueDate(dayjs().add(1, 'days'));
        fileUploadExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
        fileUploadExerciseCreation.typeMaxPoints(10);
        const problemStatement = 'This is a problem statement';
        const exampleSolution = 'E = mc^2';
        fileUploadExerciseCreation.typeProblemStatement(problemStatement);
        fileUploadExerciseCreation.typeExampleSolution(exampleSolution);
        let exercise: FileUploadExercise;
        fileUploadExerciseCreation.create().then((request: Interception) => {
            exercise = request.response!.body;
        });

        // Make sure file upload exercise is shown in exercises list
        cy.visit(`course-management/${course.id}/exercises`).then(() => {
            courseManagementExercises.getExercise(exercise.id!).should('be.visible');
        });
    });

    describe('File upload exercise deletion', () => {
        let exercise: FileUploadExercise;

        before(() => {
            cy.login(admin, '/');
            courseManagementRequest.createFileUploadExercise({ course }).then((response) => {
                exercise = response.body;
            });
        });

        it('Deletes an existing file upload exercise', () => {
            cy.login(admin, '/');
            navigationBar.openCourseManagement();
            courseManagement.openExercisesOfCourse(course.id!);
            courseManagementExercises.deleteFileUploadExercise(exercise);
            courseManagementExercises.getExercise(exercise.id!).should('not.exist');
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
