import { expect, type Page } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import dayjs from 'dayjs';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, instructor, tutor } from '../../support/users';

async function saveReviewScreenshot(page: Page, name: string) {
    const directory = process.env.ARTEMIS_SCREENSHOT_DIRECTORY;
    if (directory) {
        await mkdir(directory, { recursive: true });
        await page.screenshot({ path: path.join(directory, `${name}.png`), fullPage: true });
    }
}

// Keep navigation inside Angular: a reload would hide stale form state and breadcrumb caches.
test.describe('Tutorial group editing', { tag: '@fast' }, () => {
    let course: Course | undefined;
    let firstGroupId: number;
    let secondGroupId: number;
    let tutorId: number;
    let replacementTutorId: number;

    test.beforeEach(async ({ login, page, courseManagementAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ timeZone: 'Europe/Berlin', allowMessaging: true });
        const tutorResponse = await userManagementAPIRequests.getUser(tutor.username);
        expect(tutorResponse.ok()).toBeTruthy();
        tutorId = (await tutorResponse.json()).id;
        const instructorResponse = await userManagementAPIRequests.getUser(instructor.username);
        expect(instructorResponse.ok()).toBeTruthy();
        replacementTutorId = (await instructorResponse.json()).id;
        const configuration = await page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups-configuration`, {
            data: {
                tutorialPeriodStartInclusive: dayjs().subtract(1, 'month').format('YYYY-MM-DD'),
                tutorialPeriodEndInclusive: dayjs().add(1, 'month').format('YYYY-MM-DD'),
                useTutorialGroupChannels: true,
                usePublicTutorialGroupChannels: true,
            },
        });
        expect(configuration.ok(), await configuration.text()).toBeTruthy();
        const first = await page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups`, {
            data: { title: 'Group Alpha', tutorId, language: 'English', isOnline: false, campus: 'Garching', capacity: 12, additionalInformation: 'Alpha information' },
        });
        expect(first.ok(), await first.text()).toBeTruthy();
        firstGroupId = await first.json();
        const second = await page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups`, {
            data: { title: 'Group Beta', tutorId, language: 'English', isOnline: false },
        });
        expect(second.ok(), await second.text()).toBeTruthy();
        secondGroupId = await second.json();
        await page.goto(`/course-management/${course.id}/tutorial-groups`);
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    test('loads the selected group and clears optional fields when switching groups', async ({ page }) => {
        await page.locator(`#edit-${firstGroupId}`).click();
        await expect(page.locator('#title-input')).toHaveValue('Group Alpha');
        await expect(page.locator('#campus-input')).toHaveValue('Garching');
        await expect(page.locator('#capacity-input')).toHaveValue('12');
        await expect(page.locator('#additional-information-input')).toHaveValue('Alpha information');

        await page.locator(`a[id^="bread-crumb-"][href="/course-management/${course!.id}/tutorial-groups"]`).click();
        await page.locator(`#edit-${secondGroupId}`).click();
        await expect(page.locator('#title-input')).toHaveValue('Group Beta');
        await expect(page.locator('#campus-input')).toHaveValue('');
        await expect(page.locator('#capacity-input')).toHaveValue('');
        await expect(page.locator('#additional-information-input')).toHaveValue('');
        await saveReviewScreenshot(page, 'tutorial-group-beta-empty-fields');
    });

    test('refreshes edit and detail breadcrumbs after a rename and preserves a colliding channel on a tutor update', async ({ page }) => {
        const overview = `/course-management/${course!.id}/tutorial-groups`;
        const detail = `${overview}/${firstGroupId}`;
        const api = `api/tutorialgroup/courses/${course!.id}/tutorial-groups`;
        const collision = await page.request.post(`api/communication/courses/${course!.id}/channels`, {
            data: { name: 'tutorgroup-renamed', type: 'channel', isPublic: true, isAnnouncementChannel: false },
        });
        expect(collision.ok(), await collision.text()).toBeTruthy();

        await page.locator(`#edit-${firstGroupId}`).click();
        const breadcrumb = page.locator(`a[id^="bread-crumb-plain-"][href="${detail}"]`);
        await expect(breadcrumb).toHaveText('Group Alpha');
        await page.locator('#title-input').fill('Renamed');
        const saved = page.waitForResponse((response) => response.url().endsWith(`/${firstGroupId}`) && response.request().method() === 'PUT');
        await page.getByTestId('save-button').click();
        expect((await saved).ok()).toBeTruthy();
        await expect(page).toHaveURL(overview);
        await page.locator(`#edit-${firstGroupId}`).click();
        await expect(page.locator('#title-input')).toHaveValue('Renamed');
        await expect(breadcrumb).toHaveText('Renamed');
        await saveReviewScreenshot(page, 'tutorial-group-renamed-edit-breadcrumb');
        await breadcrumb.click();
        await expect(page).toHaveURL(detail);
        await expect(breadcrumb).toHaveText('Renamed');
        await saveReviewScreenshot(page, 'tutorial-group-renamed-detail-breadcrumb');

        const groupsResponse = await page.request.get(api);
        expect(groupsResponse.ok()).toBeTruthy();
        const groups = await groupsResponse.json();
        const renamed = groups.find((group: { id: number }) => group.id === firstGroupId);
        expect(renamed.channel.name).toMatch(/^tutorgroup-renamed-\d+$/);
        expect(renamed.channel.name.length).toBeLessThanOrEqual(30);
        const update = await page.request.put(`${api}/${firstGroupId}`, {
            data: {
                title: 'Renamed',
                tutorId: replacementTutorId,
                language: 'English',
                isOnline: false,
                campus: 'Garching',
                capacity: 12,
                additionalInformation: 'Alpha information',
            },
        });
        expect(update.ok(), await update.text()).toBeTruthy();
        const updatedGroupsResponse = await page.request.get(api);
        expect(updatedGroupsResponse.ok()).toBeTruthy();
        const updatedGroups = await updatedGroupsResponse.json();
        const updated = updatedGroups.find((group: { id: number }) => group.id === firstGroupId);
        expect(updated.channel.id).toBe(renamed.channel.id);
        expect(updated.channel.name).toBe(renamed.channel.name);
    });
});
