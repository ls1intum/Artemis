import dayjs from 'dayjs';
import { MODELING_EDITOR_CANVAS } from '../../../support/constants';

import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, instructor, studentOne } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Modeling Exercise Management', { tag: '@fast' }, () => {
    test.describe('Create Modeling Exercise', () => {
        let modelingExercise: ModelingExercise;

        test('Create a new modeling exercise', async ({ login, page, courseManagementExercises, modelingExerciseCreation, modelingExerciseEditor, modelingExerciseAssessment }) => {
            await login(instructor);
            await page.goto(`/course-management/${course.id}/exercises`);
            await page.waitForLoadState('networkidle');
            await courseManagementExercises.createModelingExercise();
            await modelingExerciseCreation.setTitle('Modeling ' + generateUUID());
            await modelingExerciseCreation.addCategories(['e2e-testing', 'test2']);
            await modelingExerciseCreation.setPoints(10);
            const response = await modelingExerciseCreation.save();
            modelingExercise = await response.json();
            await expect(courseManagementExercises.getExerciseTitle(modelingExercise.title!)).toBeAttached();
            await page.goto(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
            await page.waitForLoadState('networkidle');
            await modelingExerciseEditor.addComponentToExampleSolutionModel(1);
            await expect(page.locator(MODELING_EDITOR_CANVAS).locator('g').nth(0)).toBeAttached();
            await modelingExerciseCreation.save();

            await page.goto(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/example-submissions`);
            await page.waitForLoadState('networkidle');
            await modelingExerciseEditor.clickCreateExampleSubmission();
            await modelingExerciseEditor.addComponentToExampleSolutionModel(1);
            await modelingExerciseEditor.addComponentToExampleSolutionModel(2);
            await modelingExerciseEditor.addComponentToExampleSolutionModel(3);
            await modelingExerciseEditor.clickCreateNewExampleSubmission();
            await modelingExerciseEditor.showExampleAssessment();
            await modelingExerciseAssessment.openAssessmentForComponent(1);
            await modelingExerciseAssessment.assessComponent(-1, 'False');
            await modelingExerciseAssessment.clickNextAssessment();
            await modelingExerciseAssessment.assessComponent(2, 'Good');
            await modelingExerciseAssessment.clickNextAssessment();
            await modelingExerciseAssessment.assessComponent(0, 'Unnecessary');
            await modelingExerciseAssessment.submitExample();
            await page.goto(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
            await page.waitForLoadState('networkidle');
            await modelingExerciseEditor.waitForExampleSolutionEditor();
            await expect(modelingExerciseEditor.getModelingCanvas()).toBeVisible();
        });

        test.afterEach('Delete modeling exercise', async ({ login, exerciseAPIRequests }) => {
            if (modelingExercise) {
                await login(admin);
                await exerciseAPIRequests.deleteModelingExercise(modelingExercise.id!);
            }
        });
    });

    test.describe('Edit Modeling Exercise', () => {
        let modelingExercise: ModelingExercise;

        test.beforeEach('Create Modeling Exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        });

        test('Edit Existing Modeling Exercise', async ({ page, modelingExerciseCreation, courseManagementExercises }) => {
            await page.goto(`/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
            const newTitle = 'New Modeling Exercise Title';
            const points = 100;
            await modelingExerciseCreation.setTitle(newTitle);
            await modelingExerciseCreation.pickDifficulty({ hard: true });
            await modelingExerciseCreation.setReleaseDate(dayjs().add(1, 'day'));
            await modelingExerciseCreation.setDueDate(dayjs().add(2, 'day'));
            await modelingExerciseCreation.setAssessmentDueDate(dayjs().add(3, 'day'));
            await modelingExerciseCreation.includeInOverallScore();
            await modelingExerciseCreation.setPoints(points);
            await modelingExerciseCreation.save();
            await page.goto(`/course-management/${course.id}/exercises`);
            await expect(courseManagementExercises.getModelingExerciseTitle(modelingExercise.id!)).toContainText(newTitle);
            await expect(courseManagementExercises.getModelingExerciseMaxPoints(modelingExercise.id!)).toContainText(points.toString());
        });

        test.afterEach('Delete exercise', async ({ exerciseAPIRequests }) => {
            await exerciseAPIRequests.deleteModelingExercise(modelingExercise.id!);
        });
    });

    test.describe('Delete Modeling Exercise', () => {
        let modelingExercise: ModelingExercise;

        test.beforeEach('Create Modeling exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        });

        test('Deletes an existing Modeling exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteModelingExercise(modelingExercise);
            await expect(courseManagementExercises.getExercise(modelingExercise.id!)).not.toBeAttached();
        });

        test.afterEach('Delete exercise', async ({ exerciseAPIRequests }) => {
            await exerciseAPIRequests.deleteModelingExercise(modelingExercise.id!);
        });
    });

    test.describe('Modeling Exercise Release', () => {
        let modelingExercise: ModelingExercise;

        test('Student can not see unreleased Modeling Exercise', async ({ page, login, exerciseAPIRequests, courseOverview }) => {
            await login(instructor);
            const exerciseTitle = 'Modeling ' + generateUUID();
            modelingExercise = await exerciseAPIRequests.createModelingExercise({ course }, exerciseTitle, dayjs().add(1, 'hour'));
            await login(studentOne, `/courses/${course.id}/exercises`);
            await expect(courseOverview.getExercise(exerciseTitle)).toHaveCount(0);
        });

        test('Student can see released Modeling Exercise', async ({ login, page, exerciseAPIRequests }) => {
            await login(instructor);
            modelingExercise = await exerciseAPIRequests.createModelingExercise({ course }, 'Modeling ' + generateUUID(), dayjs().subtract(1, 'hour'));
            await login(studentOne, '/courses');
            await page.goto('/courses/' + course.id + '/exercises/' + modelingExercise.id);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
