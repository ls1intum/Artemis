import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/course/shared/entities/course.model';

import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { instructor, studentOne } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Commands } from '../../support/commands';
import { IrisChatbotWidget } from '../../support/pageobjects/iris/IrisChatbotWidget';

// Course 9022 (lectureManagement); studentOne (artemis_test_user_1) is enrolled.
const course = { id: SEED_COURSES.lectureManagement.id, title: SEED_COURSES.lectureManagement.title } as Course;

/**
 * High-fidelity E2E coverage for the Iris (AI tutor) floating chatbot widget on the
 * student lecture page, exercised against a REAL Pyris (the Iris microservice).
 *
 * The whole stack is real except the LLM: a real Pyris container talks to a real
 * Weaviate and calls back to Artemis over the genuine wire contract; only the LLM is
 * replaced by a deterministic mock OpenAI-compatible server (its canned reply contains
 * the marker "mock-llm"). See src/test/playwright/support/iris-stack/ and the runner's
 * RUN_IRIS path.
 *
 * These tests require Iris to be enabled on the server (the floating FAB is gated behind
 * `profileService.isModuleFeatureActive('iris')` AND the course Iris settings being
 * enabled). Run them with:
 *     RUN_IRIS=true ./run-e2e-tests-local-fast.sh --skip-db --filter "Iris"
 *
 * When Iris is NOT enabled (the default), the suite skips itself rather than failing, so
 * it is a no-op in normal CI runs.
 */
test.describe('Iris chatbot widget (real Pyris)', { tag: '@fast' }, () => {
    let lecture: Lecture;

    test.beforeAll(async ({ browser }) => {
        // Probe the server's module features; skip the whole suite if Iris is not active.
        const probeContext = await browser.newContext();
        const info = await probeContext.request.get('management/info');
        const features: string[] = info.ok() ? ((await info.json())?.activeModuleFeatures ?? []) : [];
        await probeContext.close();
        test.skip(!features.includes('iris'), 'Iris module feature is not active on the server (run with RUN_IRIS=true)');
    });

    test.beforeEach(async ({ courseManagementAPIRequests, page }) => {
        // Create the lecture as instructor via API (faster and more robust than the UI).
        await Commands.login(page, instructor, '/');
        // Defensively ensure the course-level Iris settings are enabled (they default to
        // enabled when no override row exists, but this is idempotent and robust to a
        // future default flip). Instructors may toggle `enabled`.
        await page.request.put(`api/iris/courses/${course.id}/iris-settings`, { data: { enabled: true, variant: 'default' } });
        lecture = await courseManagementAPIRequests.createLecture(course);
        expect(lecture.id, 'lecture should be created with an id').toBeDefined();
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        if (lecture?.id) {
            await courseManagementAPIRequests.deleteLecture(lecture.id);
        }
    });

    test('shows the floating FAB and the header controls, and maximizes to ~full overlay width', async ({ login, page }) => {
        // Desktop viewport so isMobile() does not force full-size geometry.
        await page.setViewportSize({ width: 1440, height: 900 });

        await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const widget = new IrisChatbotWidget(page);

        // The FAB appears on the lecture detail page when Iris is enabled for the course.
        await expect(widget.getFab()).toBeVisible();

        // Open the widget with a real pointer click.
        await widget.openWidget();

        // Header controls: maximize (expand) and close (xmark) are always present; the
        // info (circle-info) control is only shown when chat history is unavailable, so
        // assert it conditionally.
        await expect(widget.getMaximizeControl()).toBeVisible();
        await expect(widget.getCloseControl()).toBeVisible();
        if (await widget.getInfoControl().count()) {
            await expect(widget.getInfoControl()).toBeVisible();
        }

        // Record the non-maximized width (expected ~450px, well below the overlay width).
        const overlayWidth = await widget.getOverlayWidth();
        const normalWidth = await widget.getWidgetWidth();
        expect(normalWidth, 'widget should start clearly narrower than the overlay').toBeLessThan(overlayWidth * 0.8);

        // Maximize via a real pointer click on the expand control.
        await widget.getMaximizeControl().click();

        // After maximize the widget width is set to ~0.93 * overlay width (inline px style).
        // The expand icon flips to the compress (restore) icon.
        await expect(widget.getRestoreControl()).toBeVisible();
        await expect
            .poll(async () => widget.getWidgetWidth(), { message: 'widget should resize to ~93% of the overlay width after maximize' })
            .toBeGreaterThan(overlayWidth * 0.85);

        const maximizedWidth = await widget.getWidgetWidth();
        expect(maximizedWidth, 'maximized width should not exceed the overlay').toBeLessThanOrEqual(overlayWidth + 1);
        // Sanity: maximize materially grew the widget.
        expect(maximizedWidth, 'maximized width should be larger than the normal width').toBeGreaterThan(normalWidth);
    });

    test('sends a message and renders the assistant reply streamed back from real Pyris', async ({ login, page }) => {
        await page.setViewportSize({ width: 1440, height: 900 });

        await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const widget = new IrisChatbotWidget(page);
        await widget.openWidget();

        // Send a message. Artemis POSTs the chat pipeline run to the real Pyris, which runs
        // the course-chat pipeline, calls the mock LLM, and POSTs the result back to Artemis's
        // status callback. The UI then renders the assistant message.
        await widget.sendMessage('Hello Iris, this is an e2e test.');

        // The assistant reply arrives via the Pyris status callback. Real pipeline + callback
        // latency can be several seconds, so allow a generous timeout.
        const llmMessage = widget.getLlmMessages().first();
        await expect(llmMessage).toBeVisible({ timeout: 60_000 });
        await expect(llmMessage).toContainText('mock-llm', { timeout: 60_000 });
    });
});
