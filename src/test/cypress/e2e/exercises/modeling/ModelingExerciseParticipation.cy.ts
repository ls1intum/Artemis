import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

import { courseManagementAPIRequest, courseOverview, exerciseAPIRequest, modelingExerciseEditor } from '../../../support/artemis';
import { admin, studentOne } from '../../../support/users';
import { convertModelAfterMultiPart } from '../../../support/utils';

describe('Modeling Exercise Participation', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response: Cypress.Response<Course>) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            exerciseAPIRequest.createModelingExercise({ course }).then((resp: Cypress.Response<ModelingExercise>) => {
                modelingExercise = resp.body;
            });
        });
    });

    it('Student can start and submit their model', () => {
        cy.login(studentOne, `/courses/${course.id}`);
        courseOverview.startExercise(modelingExercise.id!);
        courseOverview.openRunningExercise(modelingExercise.id!);
        modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 1, false, 310, 320);
        modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 2, false, 730, 500);
        modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 3, false, 1000, 100);
        modelingExerciseEditor.submit();
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
