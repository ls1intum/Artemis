import dayjs from 'dayjs';

import { Lecture } from 'app/lecture/shared/entities/lecture.model';

import { instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';

import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { Fixtures } from '../../fixtures/fixtures';
import { SEED_COURSES } from '../../support/seedData';

// Common primitives
const dateFormat = 'MMM D, YYYY HH:mm';
const lectureData = {
    title: 'Lecture ' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs(),
    endDate: dayjs().add(1, 'hour'),
};

const course = { id: SEED_COURSES.lectureManagement.id, title: SEED_COURSES.lectureManagement.title } as any;

test.describe('Lecture management', { tag: '@fast' }, () => {
    let lastCreatedLecture: Lecture | undefined;

    test('Creates a lecture', async ({ login, page, lectureManagement, lectureCreation }) => {
        await login(instructor, `/course-management/${course.id}`);
        await lectureManagement.getLectures().click();
        await lectureManagement.clickCreateLecture();
        await lectureCreation.setTitle(lectureData.title);
        const description = await Fixtures.get('loremIpsum-short.txt');
        await lectureCreation.typeDescription(description!);
        await lectureCreation.setStartDate(lectureData.startDate);
        await lectureCreation.setEndDate(lectureData.endDate);
        const lectureResponse = await lectureCreation.save();
        const lecture: Lecture = (lastCreatedLecture = await lectureResponse.json());
        expect(lectureResponse.status()).toBe(201);
        await expect(page).toHaveURL(`/course-management/${course.id}/lectures/${lecture.id}/edit`);
        // Wait for all pending fetches to settle so the edit form is fully
        // hydrated before we start typing. Without this, Monaco's setValue can
        // race with Angular form hydration and our new description gets
        // overwritten by the server-loaded original.
        await page.waitForLoadState('networkidle');

        const adjustedDescription = description! + 'change to enable save button again';
        await lectureCreation.typeDescription(adjustedDescription);
        const lectureResponseFromEdit = await lectureCreation.save();
        const lectureFromEdit: Lecture = await lectureResponseFromEdit.json();
        expect(lectureResponseFromEdit.status()).toBe(200);
        await page.waitForURL(`**/${course.id}/lectures/${lectureFromEdit.id}`);

        await expect(lectureManagement.getLectureTitle()).toContainText(lectureData.title);
        await expect(lectureManagement.getLectureDescription()).toContainText(adjustedDescription!);
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

        test.beforeEach(async ({ login, page, courseManagementAPIRequests }) => {
            await login(instructor);
            lecture = lastCreatedLecture = await courseManagementAPIRequests.createLecture(course);
            await page.goto(`/course-management/${course.id}/lectures`);
            await page.waitForLoadState('networkidle');
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

        test('Can open page to add attachment unit to the lecture', async ({ lectureManagement, page }) => {
            await lectureManagement.openAttachmentUnitCreationPage(lecture.id!);
            await expect(page.getByText('Create File/Video Content')).toBeVisible();
        });
    });

    test.afterEach('Delete lecture', async ({ courseManagementAPIRequests }) => {
        if (lastCreatedLecture?.id) {
            try {
                await courseManagementAPIRequests.deleteLecture(lastCreatedLecture.id);
            } catch {
                // Lecture may already be deleted by the test
            }
            lastCreatedLecture = undefined;
        }
    });
});
