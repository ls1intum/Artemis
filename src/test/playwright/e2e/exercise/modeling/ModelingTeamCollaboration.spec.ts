import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';

import { admin, studentOne, studentTwo, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { Commands } from '../../../support/commands';
import { ExerciseMode } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';
import { newBrowserPage } from '../../../support/utils';
import { CourseOverviewPage } from '../../../support/pageobjects/course/CourseOverviewPage';
import { ModelingEditor } from '../../../support/pageobjects/exercises/modeling/ModelingEditor';

// The exercise-participation seed course has studentOne + studentTwo as students and tutor as a
// tutor, so it can host a team exercise without provisioning users at runtime.
const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * Live, multi-client collaboration on a TEAM modeling exercise.
 *
 * Two students from the SAME team open the SAME modeling exercise in two independent browser
 * contexts. When student A adds a UML element, it must appear in student B's editor *live* — the
 * editors are Yjs-backed and Yjs is the single source of truth (the legacy full-submission diagram
 * sync path was removed). This test therefore exercises the real Yjs patch relay: A's local model
 * mutation is encoded as a Yjs update, broadcast over the collaboration websocket, fanned out across
 * the cluster nodes, and applied to B's document — all without any submit/save round-trip.
 *
 * Tagged @slow: it spins up two extra browser contexts, two logins and waits for cross-node
 * websocket propagation, so it needs a larger time budget than a single-client test.
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

    test('Element added by one team member appears live in the other member editor', async ({ browser }) => {
        test.slow();
        const exerciseUrl = `/courses/${course.id}/exercises/${modelingExercise.id!}`;

        // Two fully isolated contexts (separate cookie jars) so each page authenticates as a
        // different team member — this is what makes them two distinct Yjs collaborators.
        const pageA = await newBrowserPage(browser);
        const pageB = await newBrowserPage(browser);

        try {
            await Commands.login(pageA, studentOne, exerciseUrl);
            await Commands.login(pageB, studentTwo, exerciseUrl);

            const courseOverviewA = new CourseOverviewPage(pageA);
            const courseOverviewB = new CourseOverviewPage(pageB);
            const editorA = new ModelingEditor(pageA);
            const editorB = new ModelingEditor(pageB);

            // Both members start the (shared) team participation and open the editor. Starting on
            // both pages is intentional: it forces both clients to join the same Yjs document.
            await courseOverviewA.startExercise(modelingExercise.id!);
            await courseOverviewB.startExercise(modelingExercise.id!);

            // Wait until both Apollon editors are initialized (and thus connected to the Yjs room)
            // before mutating, so A's update is relayed to an already-listening B.
            await editorA.waitForEditorReady(modelingExercise.id!);
            await editorB.waitForEditorReady(modelingExercise.id!);

            // Snapshot B's node count before A mutates, so the assertion is robust regardless of any
            // pre-existing nodes in the shared document.
            const initialCountB = await editorB.getModelNodeCount(modelingExercise.id!);
            expect(initialCountB, 'B editor was not ready (model not initialized)').toBeGreaterThanOrEqual(0);

            // Student A adds a UML class element to the live model.
            await editorA.addComponentToModel(modelingExercise.id!, 1, 310, 320);

            // Student B must observe the new element WITHOUT any submit/reload — purely via the Yjs
            // patch relayed across the cluster. Generous timeout to absorb cross-node websocket
            // propagation latency under multi-node load.
            await editorB.waitForModelNodeCount(modelingExercise.id!, initialCountB + 1, 30000);
        } finally {
            await pageA.context().close();
            await pageB.context().close();
        }
    });

    // Seed courses are persistent — no cleanup needed. The per-test team and exercise are created
    // fresh in the beforeEach hooks above.
});
