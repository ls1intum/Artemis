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
 * E2E coverage for the run-state + activity status protocol: real Pyris runs the
 * course-chat agent, the mock LLM answers ONE tool-call round (triggered by the
 * `[e2e-tool]` marker in the user message, see support/iris-mock-llm/mock_llm.py),
 * and the UI must surface the REAL tool activity live, then persist the trail on
 * the assistant message. This exercises the full chain the stage system used to
 * fake: LangChain tool hooks -> ActivityTracker -> activity snapshots -> Artemis
 * relay -> websocket -> activity feed component.
 *
 * Run with: RUN_IRIS=true ./run-e2e-tests-local-fast.sh --skip-db --filter "Iris"
 * Skips itself when the Iris module feature is not active.
 */
test.describe('Iris activity visibility (real Pyris)', { tag: '@fast' }, () => {
    let lecture: Lecture;

    test.beforeAll(async ({ browser }) => {
        const probeContext = await browser.newContext();
        const info = await probeContext.request.get('management/info');
        const features: string[] = info.ok() ? ((await info.json())?.activeModuleFeatures ?? []) : [];
        await probeContext.close();
        test.skip(!features.includes('iris'), 'Iris module feature is not active on the server (run with RUN_IRIS=true)');
    });

    test.beforeEach(async ({ courseManagementAPIRequests, page }) => {
        await Commands.login(page, instructor, '/');
        await page.request.put(`api/iris/courses/${course.id}/iris-settings`, { data: { enabled: true, variant: 'default' } });
        lecture = await courseManagementAPIRequests.createLecture(course);
        expect(lecture.id, 'lecture should be created with an id').toBeDefined();
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        if (lecture?.id) {
            await courseManagementAPIRequests.deleteLecture(lecture.id);
        }
    });

    test('shows the real tool call live, never the fake rotation, and persists the trail on the answer', async ({ login, page }) => {
        await page.setViewportSize({ width: 1440, height: 900 });

        await login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const widget = new IrisChatbotWidget(page);
        await widget.openWidget();

        // The [e2e-tool] marker makes the mock LLM request one get_course_details tool
        // round before its canned answer, so a real tool activity flows through Pyris.
        await widget.sendMessage('Hello Iris [e2e-tool] please look at the course details.');

        // The live feed must show the tool step with its translated label while the run
        // is in flight (the mock delays its follow-up answer to keep this window open).
        const liveFeed = page.locator('.iris-activity-stepper.mode-live');
        const liveStep = liveFeed.locator('.stepper-step', { hasText: 'Looking up course info' });
        await expect(liveStep).toBeVisible({ timeout: 60_000 });

        const preambleMessage = widget.getLlmMessages().filter({ hasText: 'Let me check the course details first — mock-preamble' }).first();
        await expect(preambleMessage).toBeVisible({ timeout: 60_000 });
        await expect(widget.getMessageInput()).toBeDisabled();

        // Truthful status only: the deleted 2.6s rotation strings must never render.
        await expect(page.getByText('Thinking hard')).toHaveCount(0);
        await expect(page.getByText('Analyzing context')).toHaveCount(0);
        await expect(page.getByText('Processing your request')).toHaveCount(0);

        // The assistant reply arrives via the run-state status callback.
        const llmMessages = widget.getLlmMessages();
        const finalMessage = llmMessages.last();
        await expect(finalMessage).toBeVisible({ timeout: 60_000 });
        await expect(finalMessage).toContainText('mock-llm', { timeout: 60_000 });
        await expect(widget.getMessageInput()).toBeEnabled();
        await expect(llmMessages).toHaveCount(2);
        await expect(llmMessages.nth(0)).toContainText('Let me check the course details first — mock-preamble');
        await expect(llmMessages.nth(1)).toContainText('mock-llm');

        // Once the answer landed, the live feed clears (the trail now lives on the message).
        await expect(liveFeed).toHaveCount(0, { timeout: 15_000 });

        // The persisted trail renders collapsed by default on the assistant message and
        // expands via its summary, showing the same step in trail mode.
        await expect(preambleMessage.locator('details.activity-trail')).toHaveCount(0);
        const trail = finalMessage.locator('details.activity-trail');
        await expect(trail).toBeVisible({ timeout: 15_000 });
        const trailStep = trail.locator('.iris-activity-stepper.mode-trail .stepper-step', { hasText: 'Looking up course info' });
        await expect(trailStep).toBeHidden();
        await trail.locator('summary').click();
        await expect(trailStep).toBeVisible();
    });
});
