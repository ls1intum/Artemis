/**
 * E2E tests for AI exercise-variant generation (plan Section 10, "E2E").
 * Run locally with: ./run-e2e-tests-local-fast.sh --filter "Variant"
 *
 * TODO (Sonnet): Implement the full wizard flow against a mocked-LLM profile (deterministic canned ChangePlan +
 * canned edits — a server-side test/mock profile, mirror how other Hyperion E2E flows stub the ChatClient):
 * 1. Instructor opens course exercise management, clicks "Create Variant with AI" on an exercise row
 * (use the existing CourseManagementExercisesPage flow + data-testid hooks — see project memory on
 * exercise-management E2E selectors).
 * 2. Selects an intent (e.g. difficulty), a placement, starts generation.
 * 3. Progress steps advance via websocket events; the wizard can be CLOSED mid-run and the navbar tray shows the
 * spinner (plan Section 5.4).
 * 4. On DONE, assert the variant appears in the course exercise list / variant group (plan Section 10).
 * 5. Cancel path: start a job, cancel from the tray with confirmation, assert the entry shows CANCELLED and no
 * variant exercise exists.
 *
 * TODO (Sonnet): Before the PR, one manual multi-node sanity run:
 * ./run-e2e-tests-local-multinode-fast.sh --filter "Variant" — the job map is Hazelcast-backed and the websocket
 * event must reach the user regardless of which node runs the job (plan Section 10, last bullet).
 */
import { test } from '../../support/fixtures';

test.describe.skip('Exercise variant generation with AI', () => {
    // TODO (Sonnet): remove .skip and implement — see file header. Verify the fixtures import path matches the
    // neighboring specs in this directory before implementing.
});
