import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { admin, studentOne } from '../../support/users';
import { test } from '../../support/fixtures';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.lectureManagement.id } as any;

/**
 * Regression guard for the fullscreen-layout content projection: Angular's `ng-content select` does not match
 * projectable content nested inside @if/@else, so wrapping the attachment-video-unit's content-main slot in @if
 * left the lecture unit blank when expanded (clicking a video or PDF unit did nothing). This is a browser-only
 * failure that jsdom/Vitest does not reproduce, so it must be covered end-to-end.
 *
 * A YouTube video unit needs no file upload, and `.video-player-container` is rendered by both the YouTube-player
 * branch and the iframe fallback, so the assertion holds whether or not the YouTube API loads in CI.
 */
test.describe('Lecture unit content renders when expanded', { tag: '@fast' }, () => {
    let lecture: any;

    test.beforeEach('Create a lecture with a video unit', async ({ login, courseManagementAPIRequests, page }) => {
        await login(admin);
        lecture = await courseManagementAPIRequests.createLecture(course);
        const response = await page.request.post(`api/lecture/lectures/${lecture.id}/attachment-video-units`, {
            multipart: {
                attachmentVideoUnit: {
                    name: 'attachmentVideoUnit',
                    mimeType: 'application/json',
                    buffer: Buffer.from(
                        JSON.stringify({
                            type: 'attachment',
                            name: 'Projection Regression Unit',
                            videoSource: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
                            releaseDate: dayjs().subtract(1, 'hour'),
                        }),
                    ),
                },
            },
        });
        expect(response.ok(), `creating the attachment video unit failed: ${response.status()}`).toBeTruthy();
    });

    test('projects the video content into the fullscreen-layout content-main slot', async ({ login, page }) => {
        await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);

        const toggle = page.locator('#lecture-unit-toggle-button').first();
        await expect(toggle).toBeVisible({ timeout: 30_000 });
        await toggle.click();

        // The content-main projection slot must render the video block. Before the fix it was empty.
        await expect(page.locator('[content-main]')).toBeAttached({ timeout: 30_000 });
        await expect(page.locator('.video-player-container').first()).toBeVisible({ timeout: 30_000 });
    });
});
