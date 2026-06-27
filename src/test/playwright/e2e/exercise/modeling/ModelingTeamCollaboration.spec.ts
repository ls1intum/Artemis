import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';

import { admin, studentOne, studentTwo, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect, type Browser } from '@playwright/test';
import { Commands } from '../../../support/commands';
import { ExerciseMode } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';
import { newBrowserPage } from '../../../support/utils';
import { CourseOverviewPage } from '../../../support/pageobjects/course/CourseOverviewPage';
import { ModelingEditor } from '../../../support/pageobjects/exercises/modeling/ModelingEditor';

// The exercise-participation seed course has studentOne + studentTwo as students and tutor as a
// tutor, so it can host a team exercise without provisioning users at runtime.
const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

const SYNC_TIMEOUT = 30000;

/**
 * Live, multi-client collaboration on a TEAM modeling exercise.
 *
 * Two students from the SAME team open the SAME modeling exercise in two independent browser
 * contexts. The editors are Yjs-backed and Yjs is the single source of truth (the legacy
 * full-submission diagram sync path was removed), so changes propagate purely via the Yjs patch
 * relay over the collaboration websocket, fanned out across the cluster nodes — no submit/save
 * round-trip. These tests mirror the behaviours the Apollon standalone webapp relies on
 * (live document sync, bidirectional convergence, presence), validated here over the real
 * multi-node websocket transport.
 *
 * Tagged @slow: two extra browser contexts, two logins, and cross-node websocket propagation
 * waits need a larger time budget than a single-client test.
 */
test.describe('ModelingTeamCollaboration', { tag: '@slow' }, () => {
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create team modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course }, undefined, undefined, undefined, undefined, {
            mode: ExerciseMode.TEAM,
            teamAssignmentConfig: { minTeamSize: 2, maxTeamSize: 3 },
        });
    });

    test.beforeEach('Create a team with both students', async ({ login, userManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        const students = await Promise.all(
            [studentOne, studentTwo].map(async (student) => {
                const response = await userManagementAPIRequests.getUser(student.username);
                return response.json();
            }),
        );
        const tutorUser = await (await userManagementAPIRequests.getUser(tutor.username)).json();
        const response = await exerciseAPIRequests.createTeam(modelingExercise.id!, students, tutorUser);
        expect(response.ok(), `Team creation failed: ${response.status()} ${response.statusText()}`).toBeTruthy();
        const team: Team = await response.json();
        expect(team.id, 'Created team is missing an id').toBeTruthy();
    });

    /**
     * Opens the exercise as both team members in two fully isolated browser contexts (separate cookie
     * jars, so each authenticates as a different student — i.e. two distinct Yjs collaborators) and
     * waits until both Apollon editors are live and joined to the same Yjs room.
     */
    async function openTwoCollaborators(browser: Browser) {
        const exerciseUrl = `/courses/${course.id}/exercises/${modelingExercise.id!}`;
        const pageA = await newBrowserPage(browser);
        const pageB = await newBrowserPage(browser);

        await Commands.login(pageA, studentOne, exerciseUrl);
        await Commands.login(pageB, studentTwo, exerciseUrl);

        const editorA = new ModelingEditor(pageA);
        const editorB = new ModelingEditor(pageB);

        // Starting on both pages is intentional: it forces both clients to join the same Yjs document.
        await new CourseOverviewPage(pageA).startExercise(modelingExercise.id!);
        await new CourseOverviewPage(pageB).startExercise(modelingExercise.id!);

        await editorA.waitForEditorReady(modelingExercise.id!);
        await editorB.waitForEditorReady(modelingExercise.id!);

        return { pageA, pageB, editorA, editorB };
    }

    test('Element added by one team member appears live in the other member editor', async ({ browser }) => {
        test.slow();
        const { pageA, pageB, editorA, editorB } = await openTwoCollaborators(browser);
        try {
            // Snapshot B's node count first, so the assertion is robust regardless of any pre-existing nodes.
            const initialCountB = await editorB.getModelNodeCount(modelingExercise.id!);
            expect(initialCountB, 'B editor was not ready (model not initialized)').toBeGreaterThanOrEqual(0);

            await editorA.addComponentToModel(modelingExercise.id!, 1, 310, 320);

            // B observes A's element WITHOUT any submit/reload — purely via the Yjs patch relayed across the cluster.
            await editorB.waitForModelNodeCount(modelingExercise.id!, initialCountB + 1, SYNC_TIMEOUT);
        } finally {
            await pageA.context().close();
            await pageB.context().close();
        }
    });

    test('Concurrent edits from both members converge in both editors', async ({ browser }) => {
        test.slow();
        const { pageA, pageB, editorA, editorB } = await openTwoCollaborators(browser);
        try {
            const initial = await editorA.getModelNodeCount(modelingExercise.id!);
            expect(initial, 'Editors not ready (model not initialized)').toBeGreaterThanOrEqual(0);

            // A adds an element; both editors converge to initial + 1 (A -> B direction).
            await editorA.addComponentToModel(modelingExercise.id!, 1, 220, 200);
            await editorA.waitForModelNodeCount(modelingExercise.id!, initial + 1, SYNC_TIMEOUT);
            await editorB.waitForModelNodeCount(modelingExercise.id!, initial + 1, SYNC_TIMEOUT);

            // B then adds on top of A's element; both converge to initial + 2 (B -> A direction, and
            // neither member's edit is clobbered — the CRDT merges both adds).
            await editorB.addComponentToModel(modelingExercise.id!, 2, 620, 200);
            await editorB.waitForModelNodeCount(modelingExercise.id!, initial + 2, SYNC_TIMEOUT);
            await editorA.waitForModelNodeCount(modelingExercise.id!, initial + 2, SYNC_TIMEOUT);
        } finally {
            await pageA.context().close();
            await pageB.context().close();
        }
    });

    test('Each member sees the other in the collaboration presence bar', async ({ browser }) => {
        test.slow();
        const { pageA, pageB } = await openTwoCollaborators(browser);
        try {
            // Apollon renders the presence bar only when a remote collaborator is present, so its
            // visibility on each page proves the other member's awareness propagated across the cluster.
            await expect(pageA.locator('.apollon-collaboration-presence-bar')).toBeVisible({ timeout: SYNC_TIMEOUT });
            await expect(pageB.locator('.apollon-collaboration-presence-bar')).toBeVisible({ timeout: SYNC_TIMEOUT });
        } finally {
            await pageA.context().close();
            await pageB.context().close();
        }
    });

    // Seed courses are persistent — no cleanup needed. The per-test team and exercise are created
    // fresh in the beforeEach hooks above.
});
