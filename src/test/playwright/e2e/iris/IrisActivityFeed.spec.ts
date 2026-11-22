import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/course/shared/entities/course.model';

import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { instructor, studentThree, studentTwo } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Commands } from '../../support/commands';
import { IrisChat } from '../../support/pageobjects/iris/IrisChat';
import { enableIrisForCourse } from '../../support/irisSetup';

// Course 9022 (lectureManagement); studentTwo (artemis_test_user_2) and studentThree (artemis_test_user_3) are enrolled.
//
// This suite deliberately uses a DIFFERENT student than IrisLectureChat.spec.ts: the server reuses
// today's still-empty COURSE_CHAT session per (user, course) (IrisChatSessionService#findOrCreateEmptyCourseSession),
// so two specs running in parallel as the same student in the same course land in the SAME chat session and
// see each other's messages — which broke the exact-message-count assertions below (issue #13301).
// For the same reason the two tests below use two different students: they run in parallel.
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
 * Run with: RUN_IRIS=true ./supporting_scripts/e2e/run-e2e-tests-local-fast.sh --skip-db --filter "Iris"
 * Skips itself when the Iris module feature is not active.
 *
 * Tagged `@slow`, not `@fast`: this test waits out a full two-round Pyris agent run (tool call plus
 * follow-up answer, each relayed back over the status callback) and its assertions already budget
 * 60s per wait — more than the `@fast` project's whole per-test cap (45s in the nightly runner).
 * See the same note in IrisLectureChat.spec.ts and issue #13383.
 */
test.describe('Iris activity visibility (real Pyris)', { tag: '@slow' }, () => {
    let lecture: Lecture;

    test.beforeAll(async ({ browser }) => {
        const probeContext = await browser.newContext();
        const info = await probeContext.request.get('management/info');
        const features: string[] = info.ok() ? ((await info.json())?.activeModuleFeatures ?? []) : [];
        await probeContext.close();
        test.skip(!features.includes('iris'), 'Iris module feature is not active on the server (run with RUN_IRIS=true)');
    });

    test.beforeEach(async ({ courseManagementAPIRequests, page }) => {
        // No target URL: the calls below only need the instructor JWT cookie, and the test navigates
        // as the student right after. See IrisLectureChat.spec.ts for why the discarded `/` bootstrap
        // this used to do was the nightly's timeout hot spot (issue #13383).
        await Commands.login(page, instructor);
        await enableIrisForCourse(page.request, course.id!);
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

        await login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const chat = new IrisChat(page);
        await chat.openChat();

        // The [e2e-tool] marker makes the mock LLM request one get_course_details tool
        // round before its canned answer, so a real tool activity flows through Pyris.
        await chat.sendMessage('Hello Iris [e2e-tool] please look at the course details.');

        // The live group must hold the tool step with its translated label while the run
        // is in flight (the mock delays its follow-up answer to keep this window open).
        const liveFeed = page.locator('.iris-activity-group.mode-live');
        await expect(liveFeed).toBeVisible({ timeout: 60_000 });
        await expect(liveFeed.locator('.iris-activity-row', { hasText: 'Looking up course info' })).toHaveCount(1);

        const preambleMessage = chat.getLlmMessages().filter({ hasText: 'Let me check the course details first — mock-preamble' }).first();
        await expect(preambleMessage).toBeVisible({ timeout: 60_000 });
        await expect(chat.getMessageInput()).toBeDisabled();

        // Truthful status only: the deleted 2.6s rotation strings must never render.
        await expect(page.getByText('Thinking hard')).toHaveCount(0);
        await expect(page.getByText('Analyzing context')).toHaveCount(0);
        await expect(page.getByText('Processing your request')).toHaveCount(0);

        // The assistant reply arrives via the run-state status callback.
        const llmMessages = chat.getLlmMessages();
        const finalMessage = llmMessages.last();
        await expect(finalMessage).toBeVisible({ timeout: 60_000 });
        await expect(finalMessage).toContainText('mock-llm', { timeout: 60_000 });
        await expect(chat.getMessageInput()).toBeEnabled();
        await expect(llmMessages).toHaveCount(2);
        await expect(llmMessages.nth(0)).toContainText('Let me check the course details first — mock-preamble');
        await expect(llmMessages.nth(1)).toContainText('mock-llm');

        // Once the answer landed, the live feed clears (the trail now lives on the message).
        await expect(liveFeed).toHaveCount(0, { timeout: 15_000 });

        // The step ran after the preamble, so it sits on the final answer, not on the preamble.
        // The saved group renders collapsed and expands via its summary.
        await expect(preambleMessage.locator('details.activity-trail')).toHaveCount(0);
        const trail = finalMessage.locator('details.activity-trail.mode-trail');
        await expect(trail).toBeVisible({ timeout: 15_000 });
        const trailStep = trail.locator('.iris-activity-row', { hasText: 'Looking up course info' });
        await expect(trailStep).toBeHidden();
        await trail.locator('summary').click();
        await expect(trailStep).toBeVisible();
    });

    test('shows each tool step between the message before it and the message after it', async ({ login, page }) => {
        await page.setViewportSize({ width: 1440, height: 900 });

        await login(studentThree, `/courses/${course.id}/lectures/${lecture.id}`);
        await Commands.ensureRendered(page);

        const chat = new IrisChat(page);
        await chat.openChat();

        // The [e2e-two-tool-rounds] marker makes the mock LLM write a preamble and call a tool twice
        // before its canned answer: preamble 1, step 1, preamble 2, step 2, answer.
        await chat.sendMessage('Hello Iris [e2e-two-tool-rounds] please look at the course details.');

        const llmMessages = chat.getLlmMessages();
        const secondPreamble = llmMessages.filter({ hasText: 'Let me look once more — mock-second-preamble' }).first();
        await expect(secondPreamble).toBeVisible({ timeout: 60_000 });

        // Step 1 moved onto the second preamble, so the live group holds only step 2. Both steps run the
        // same tool, so the step id tells them apart.
        const liveFeed = page.locator('.iris-activity-group.mode-live');
        await expect(liveFeed.locator('.iris-activity-row[data-activity-id="act-2"]')).toHaveCount(1, { timeout: 60_000 });
        await expect(liveFeed.locator('.iris-activity-row')).toHaveCount(1);

        await expect(llmMessages.last()).toContainText('mock-llm', { timeout: 60_000 });
        await expect(llmMessages).toHaveCount(3);
        await expect(liveFeed).toHaveCount(0, { timeout: 15_000 });

        // Preamble 1 came before any step, so it has no group. Preamble 2 and the answer each show one step.
        await expect(llmMessages.nth(0)).toContainText('mock-preamble');
        await expect(llmMessages.nth(0).locator('details.activity-trail')).toHaveCount(0);
        for (const index of [1, 2]) {
            const group = llmMessages.nth(index).locator('details.activity-trail.mode-trail');
            await expect(group).toBeVisible();
            await expect(group.locator('.iris-activity-row')).toHaveCount(1);
        }

        // After a reload the groups stay where they were, because each message stores its own steps.
        await page.reload();
        await Commands.ensureRendered(page);
        await chat.openChat();
        const reloadedMessages = chat.getLlmMessages();
        await expect(reloadedMessages).toHaveCount(3, { timeout: 30_000 });
        await expect(reloadedMessages.nth(0).locator('details.activity-trail')).toHaveCount(0);
        for (const index of [1, 2]) {
            await expect(reloadedMessages.nth(index).locator('details.activity-trail .iris-activity-row')).toHaveCount(1);
        }
    });
});
