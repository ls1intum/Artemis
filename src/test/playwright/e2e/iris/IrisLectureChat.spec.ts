import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/course/shared/entities/course.model';

import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { instructor, studentOne } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Commands } from '../../support/commands';
import { IrisChat } from '../../support/pageobjects/iris/IrisChat';

// Course 9022 (lectureManagement); studentOne (artemis_test_user_1) is enrolled.
//
// Keep this student distinct from IrisActivityFeed.spec.ts: the server reuses today's still-empty
// COURSE_CHAT session per (user, course), so two specs running in parallel as the same student in the
// same course would share one chat session and see each other's messages (issue #13301).
//
// The same applies WITHIN this file — `fullyParallel: true` parallelises tests in a file too, so the two
// tests below also share one session. That is only safe because the first test never asserts on message
// content. Any new test here that does must either use its own student or start a new chat first.
const course = { id: SEED_COURSES.lectureManagement.id, title: SEED_COURSES.lectureManagement.title } as Course;

/**
 * High-fidelity E2E coverage for the Iris (AI tutor) chat panel on the student lecture
 * page, exercised against a REAL Pyris (the Iris microservice).
 *
 * The whole stack is real except the LLM: a real Pyris container talks to a real
 * Weaviate and calls back to Artemis over the genuine wire contract; only the LLM is
 * replaced by a deterministic mock OpenAI-compatible server (its canned reply contains
 * the marker "mock-llm"). See src/test/playwright/support/iris-stack/ and the runner's
 * RUN_IRIS path.
 *
 * These tests require Iris to be enabled on the server (the panel is gated behind
 * `profileService.isModuleFeatureActive('iris')` AND the course Iris settings being
 * enabled). Run them with:
 *     RUN_IRIS=true ./run-e2e-tests-local-fast.sh --skip-db --filter "Iris"
 *
 * When Iris is NOT enabled (the default), the suite skips itself rather than failing, so
 * it is a no-op in normal CI runs.
 *
 * Tagged `@slow`, not `@fast`: a single test here drives a real Pyris pipeline run (Weaviate
 * lookup, mock-LLM call, status callback back into Artemis) on top of a cold Angular dev-server
 * route, and the assertions below already budget 60s for that round trip — which the `@fast`
 * project's per-test cap (45s in the nightly runner) can never honour. Measured first-attempt
 * durations on the nightly's 4-core runner were 28-45s against that 45s cap, so the monitor was
 * only ever green when its single retry rescued it (issue #13383).
 */
test.describe('Iris lecture chat (real Pyris)', { tag: '@slow' }, () => {
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
        // Authenticate WITHOUT a target URL: the two calls below only need the JWT cookie, and the
        // test's own `login(student, lectureUrl)` navigates anyway. Passing a URL here would add a
        // full Angular bootstrap of `/` (goto + load event + navbar identity check, each with a
        // reload fallback) whose rendered page is then thrown away — on the nightly runner that
        // discarded navigation is exactly where the beforeEach hook timed out (issue #13383).
        await Commands.login(page, instructor);
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

    test('shows the chat panel beside the lecture and collapses it to the icon rail', async ({ login, page }) => {
        // Desktop viewport, so the panels render as a split rather than as a single tabbed panel.
        await page.setViewportSize({ width: 1440, height: 900 });

        await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const chat = new IrisChat(page);

        // The Iris panel appears beside the lecture content when Iris is enabled for the course.
        await chat.openChat();
        await expect(chat.getChat()).toBeVisible();

        // Collapsing leaves the panels mounted and shows the icon rail, from which the chat reopens.
        await chat.getCollapseControl().click();
        await expect(chat.getChat()).toBeHidden();
        await expect(chat.getCollapsedPanelTab()).toBeVisible();

        await chat.getCollapsedPanelTab().click();
        await expect(chat.getChat()).toBeVisible();
        await expect(chat.getMessageInput()).toBeVisible();
    });

    test('sends a message and renders the assistant reply streamed back from real Pyris', async ({ login, page }) => {
        await page.setViewportSize({ width: 1440, height: 900 });

        await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const chat = new IrisChat(page);
        await chat.openChat();

        // Send a message. Artemis POSTs the chat pipeline run to the real Pyris, which runs
        // the course-chat pipeline, calls the mock LLM, and POSTs the result back to Artemis's
        // status callback. The UI then renders the assistant message.
        await chat.sendMessage('Hello Iris, this is an e2e test.');

        // The assistant reply arrives via the Pyris status callback. Real pipeline + callback
        // latency can be several seconds, so allow a generous timeout.
        const llmMessage = chat.getLlmMessages().first();
        await expect(llmMessage).toBeVisible({ timeout: 60_000 });
        await expect(llmMessage).toContainText('mock-llm', { timeout: 60_000 });
    });
});
