import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';

import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';

import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { Fixtures } from '../../fixtures/fixtures';

// Common primitives
const dateFormat = 'MMM D, YYYY HH:mm';
const lectureData = {
    title: 'Lecture ' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs(),
    endDate: dayjs().add(1, 'hour'),
};

test.describe('Lecture management', () => {
    let course: Course;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
    });

    test('Creates a lecture', async ({ login, page, lectureManagement, lectureCreation }) => {
        await login(instructor, `/course-management/${course.id}`);
        await lectureManagement.getLectures().click();
        await lectureManagement.clickCreateLecture();
        await lectureCreation.setTitle(lectureData.title);
        const description = await Fixtures.get('loremIpsum-short.txt');
        await lectureCreation.typeDescription(description!);
        await lectureCreation.setVisibleDate(lectureData.visibleDate);
        await lectureCreation.setStartDate(lectureData.startDate);
        await lectureCreation.setEndDate(lectureData.endDate);
        const lectureResponse = await lectureCreation.save();
        const lecture: Lecture = await lectureResponse.json();
        expect(lectureResponse.status()).toBe(201);

        await page.waitForURL(`**/${course.id}/lectures/${lecture.id}`);
        await expect(lectureManagement.getLectureTitle()).toContainText(lectureData.title);
        await expect(lectureManagement.getLectureDescription()).toContainText(description!);
        await expect(lectureManagement.getLectureVisibleDate()).toContainText(lectureData.visibleDate!.format(dateFormat));
        await expect(lectureManagement.getLectureStartDate()).toContainText(lectureData.startDate!.format(dateFormat));
        await expect(lectureManagement.getLectureEndDate()).toContainText(lectureData.endDate!.format(dateFormat));
        await expect(lectureManagement.getLectureCourse()).toContainText(course.title!);
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
