import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import classDiagram from '../../../../javascript/spec/helpers/sample/modeling/test-models/class-diagram.json';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { MODELING_EXERCISE_BASE } from '../../../support/constants';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

test.describe('Modeling Exercise Participation', { tag: '@fast' }, () => {
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    test('Student can start and submit their model', async ({ login, courseOverview, modelingExerciseEditor }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);
        await modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 1, 310, 320);
        await modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 2, 730, 500);
        await modelingExerciseEditor.addComponentToModel(modelingExercise.id!, 3, 1000, 100);
        await courseOverview.submitExercise('api/modeling/exercises/*/modeling-submissions');
    });

    test('Student can expand and reopen a published version-3 example solution', async ({ login, page }) => {
        const response = await page.request.put(MODELING_EXERCISE_BASE, {
            data: {
                id: modelingExercise.id,
                courseId: course.id,
                title: modelingExercise.title,
                maxPoints: modelingExercise.maxPoints,
                includedInOverallScore: modelingExercise.includedInOverallScore,
                diagramType: modelingExercise.diagramType,
                releaseDate: dayjs().subtract(5, 'days').toISOString(),
                startDate: dayjs().subtract(4, 'days').toISOString(),
                dueDate: dayjs().subtract(3, 'days').toISOString(),
                assessmentDueDate: dayjs().subtract(2, 'days').toISOString(),
                exampleSolutionPublicationDate: dayjs().subtract(1, 'day').toISOString(),
                exampleSolutionModel: JSON.stringify(classDiagram),
                exampleSolutionExplanation: 'Compare the classes and their relationships.',
            },
        });
        expect(response.ok(), await response.text()).toBeTruthy();

        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        const toggle = page.getByTestId('example-solution-toggle');
        // Apollon nests SVGs for individual nodes inside the exported diagram.
        const diagram = page.locator('jhi-modeling-editor svg').filter({ hasText: 'Class In Package' }).first();
        await expect(diagram).toBeHidden();
        await toggle.click();
        await expect(diagram).toBeVisible();
        await expect(diagram).toContainText('Connected Class');
        await expect(page.getByText('Compare the classes and their relationships.', { exact: true })).toBeVisible();

        await toggle.click();
        await expect(diagram).toBeHidden();
        await toggle.click();
        await expect(diagram).toBeVisible();
    });

    // Seed courses are persistent — no cleanup needed
});
