import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';

test.describe('Modeling Exercise Participation', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    test('Student can start and submit their model', async ({ login, courseOverview, modelingExerciseEditor }) => {
        await login(studentOne, `/courses/${course.id}`);
        await courseOverview.startExercise(modelingExercise.id!);
        await courseOverview.openRunningExercise(modelingExercise.id!);
        await modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 1, 310, 320);
        await modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 2, 730, 500);
        await modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 3, 1000, 100);
        await modelingExerciseEditor.submit();
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
