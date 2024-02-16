import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';

import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';

import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { Fixtures } from '../../fixtures/fixtures';

test.describe('Lecture management', () => {
    let course: Course;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
    });

    test('Creates a lecture', async ({ login, lectureManagement, lectureCreation }) => {
        const lectureTitle = 'Lecture ' + generateUUID();
        await login(instructor, `/course-management/${course.id}`);
        await lectureManagement.getLectures().click();
        await lectureManagement.clickCreateLecture();
        await lectureCreation.setTitle(lectureTitle);
        const text = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit'; // Simplified for example purposes
        await lectureCreation.typeDescription(text);
        await lectureCreation.setVisibleDate(dayjs());
        await lectureCreation.setStartDate(dayjs());
        await lectureCreation.setEndDate(dayjs().add(1, 'hour'));
        const lectureResponse = await lectureCreation.save();
        expect(lectureResponse.status()).toBe(201);
    });

    test('Deletes a lecture', async ({ page, login, courseManagementAPIRequests, lectureManagement }) => {
        await login(instructor, '/');
        const lecture = await courseManagementAPIRequests.createLecture(course);
        await page.goto('/course-management/' + course.id + '/lectures');
        const resp = await lectureManagement.deleteLecture(lecture);
        expect(resp.status()).toBe(200);
        await expect(lectureManagement.getLecture(lecture.id!)).not.toBeVisible();
    });

    test.describe('Handle existing lecture', () => {
        let lecture: Lecture;

        test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
            await login(instructor, `/course-management/${course.id}/lectures`);
            lecture = await courseManagementAPIRequests.createLecture(course);
        });

        test('Deletes an existing lecture', async ({ lectureManagement }) => {
            const resp = await lectureManagement.deleteLecture(lecture);
            expect(resp.status()).toBe(200);
            await expect(lectureManagement.getLecture(lecture.id!)).not.toBeVisible();
        });

        test('Adds a text unit to the lecture', async ({ lectureManagement, page }) => {
            const text = await Fixtures.get('loremIpsum-short.txt');
            await lectureManagement.openUnitsPage(lecture.id!);
            await lectureManagement.addTextUnit('Text unit', text!);
            await expect(page.getByText('Text unit')).toBeVisible();
        });

        test('Adds an exercise unit to the lecture', async ({ exerciseAPIRequests, lectureManagement, page }) => {
            const exercise = await exerciseAPIRequests.createModelingExercise({ course });
            await lectureManagement.openUnitsPage(lecture.id!);
            await lectureManagement.addExerciseUnit(exercise.id!);
            await expect(page.locator('.exercise-title', { hasText: new RegExp(`^${exercise.title!}$`) })).toBeVisible();
        });
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
