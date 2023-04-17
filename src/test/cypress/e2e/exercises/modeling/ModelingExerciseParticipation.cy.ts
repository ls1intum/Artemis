import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest, courseOverview, modelingExerciseEditor } from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';

// Common primitives
let course: Course;
let modelingExercise: ModelingExercise;

describe('Modeling Exercise Participation Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response: Cypress.Response<Course>) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.createModelingExercise({ course }).then((resp: Cypress.Response<ModelingExercise>) => {
                modelingExercise = resp.body;
            });
        });
    });

    after('Delete the test course', () => {
        cy.login(admin);
        courseManagementRequest.deleteCourse(course.id!);
    });

    it('Student can start and submit their model', () => {
        cy.login(studentOne, `/courses/${course.id}`);
        cy.reloadUntilFound('#start-exercise-' + modelingExercise.id);
        courseOverview.startExercise(modelingExercise.id!);
        cy.get('#open-exercise-' + modelingExercise.id).click();
        modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 1, false, 310, 320);
        modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 2, false, 730, 500);
        modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 3, false, 1000, 100);
        modelingExerciseEditor.submit();
    });
});
