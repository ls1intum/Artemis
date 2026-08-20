import { APIRequest, APIRequestContext, expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { generateUUID } from '../../support/utils';

/**
 * Cross-node behaviour that only a real multi-node stack can show.
 *
 * <p>
 * The rest of the suite runs through the load balancer and therefore cannot say <em>which</em> node answered. These
 * tests address two core nodes directly, so they can write on one and read on the other — which is the only way to tell
 * genuinely shared state from state that merely happens to look right because one node served both halves of the
 * interaction.
 *
 * Each case covers state held through the distributed data provider that has no other end-to-end coverage and where a
 * regression is silent:
 * <ul>
 *   <li>the per-node blob caches, which stay coherent only through a cross-node eviction broadcast,</li>
 *   <li>the distributed set behind the "one plagiarism check per course" rule,</li>
 *   <li>the distributed map behind the admin feature toggles, and</li>
 *   <li>the distributed map holding one signing key per registered LTI platform.</li>
 * </ul>
 *
 * Backend-agnostic on purpose: Artemis supports Hazelcast and Redis interchangeably, and both must pass this unchanged.
 *
 * Skipped unless MULTI_NODE_URLS names at least two core nodes, which the multi-node runners export.
 */

const NODE_URLS = (process.env.MULTI_NODE_URLS ?? '')
    .split(',')
    .map((url) => url.trim())
    .filter((url) => url.length > 0);

// Serial: the feature toggle case switches plagiarism checks off for the whole deployment for a moment, which would
// otherwise reject the plagiarism case running next to it in another worker.
test.describe.configure({ mode: 'serial' });

test.describe('Distributed state coherence', { tag: '@multi-node' }, () => {
    test.skip(NODE_URLS.length < 2, 'needs MULTI_NODE_URLS to name at least two core nodes');

    let course: Course;

    test.beforeEach('Create a course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
    });

    test.afterEach('Delete the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('A replaced file is served fresh by the node that did not handle the write', async ({ login, exerciseAPIRequests, playwright }) => {
        // The file caches are per-node rather than shared, because their values are whole file contents. A write on one
        // node therefore has to reach the others as an eviction broadcast, or they keep answering with the bytes they
        // cached earlier. Replacing the file of an existing submission under the same name is the case where the stored
        // path stays the same, so a stale entry is actually reachable.
        await login(instructor);
        const exercise = await exerciseAPIRequests.createFileUploadExercise({ course });

        await login(studentOne);
        await exerciseAPIRequests.startExerciseParticipation(exercise.id!);

        const uploadingNode = await contextFor(playwright.request, NODE_URLS[0], studentOne);
        const readingNode = await contextFor(playwright.request, NODE_URLS[1], studentOne);
        try {
            const firstPath = await submitFile(uploadingNode, exercise.id!, undefined, false, 'FIRST-CONTENT');
            // Read through both nodes so each one caches the first version in its own blob cache.
            expect(await readFile(uploadingNode, firstPath)).toBe('FIRST-CONTENT');
            expect(await readFile(readingNode, firstPath)).toBe('FIRST-CONTENT');

            const secondPath = await submitFile(uploadingNode, exercise.id!, submissionIdFromPath(firstPath), true, 'SECOND-CONTENT');
            expect(secondPath, 'replacing the file of the same submission must reuse the stored path').toBe(firstPath);

            expect(await readFile(uploadingNode, secondPath), 'the node that handled the write must not serve its own stale entry').toBe('SECOND-CONTENT');
            await expect
                .poll(async () => readFile(readingNode, secondPath), {
                    message: 'the other node kept serving the previous file, so the eviction broadcast did not reach it',
                    timeout: 15_000,
                    intervals: [250, 500, 1_000],
                })
                .toBe('SECOND-CONTENT');
        } finally {
            await uploadingNode.dispose();
            await readingNode.dispose();
        }
    });

    test('A plagiarism check running on one node blocks the same course on the other', async ({ page, login, exerciseAPIRequests, playwright }) => {
        // "One active plagiarism check per course" is enforced through a distributed set. If that set were per node,
        // both nodes would happily start their own run over the same submissions.
        await login(instructor);
        const exercise = await exerciseAPIRequests.createTextExercise({ course });

        // JPlag needs at least two comparable submissions, and enough text that a run lasts long enough for a request
        // sent to the other node at the same moment to arrive while the first one still holds the course.
        const submissionText = `${'The quick brown fox jumps over the lazy dog. '.repeat(200)}${generateUUID()}`;
        for (const student of [studentOne, studentTwo]) {
            await login(student);
            await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
            const submission = await page.request.post(`api/text/exercises/${exercise.id}/text-submissions`, {
                data: { submissionExerciseType: 'text', text: submissionText, submitted: true },
            });
            expect(submission.status(), `text submission of ${student.username}: ${await bodyOf(submission)}`).toBe(200);
        }

        const startingNode = await contextFor(playwright.request, NODE_URLS[0], instructor);
        const competingNode = await contextFor(playwright.request, NODE_URLS[1], instructor);
        try {
            const checkUrl = `api/text/text-exercises/${exercise.id}/check-plagiarism?similarityThreshold=0&minimumScore=0&minimumSize=0`;

            // Fire at both nodes in the same tick, so the second request arrives while the first run still holds the
            // course. Exactly one of them may run; the other has to be refused because the set is shared. A run over
            // two submissions can finish before the second request is served, so give the race a few attempts.
            let refusal: string | undefined;
            let lastOutcome = '';
            for (let attempt = 1; attempt <= 5 && refusal === undefined; attempt++) {
                const [first, second] = await Promise.all([startingNode.get(checkUrl, { timeout: 120_000 }), competingNode.get(checkUrl, { timeout: 120_000 })]);
                const outcomes = [
                    { node: NODE_URLS[0], status: first.status(), body: await bodyOf(first) },
                    { node: NODE_URLS[1], status: second.status(), body: await bodyOf(second) },
                ];
                lastOutcome = outcomes.map((outcome) => `${outcome.node} -> ${outcome.status} ${outcome.body}`).join(' | ');

                const accepted = outcomes.filter((outcome) => outcome.status === 200);
                const refused = outcomes.filter((outcome) => outcome.status === 400 && outcome.body.includes('oneActivePlagiarismCheck'));
                expect(accepted.length + refused.length, `attempt ${attempt}: unexpected plagiarism check outcome: ${lastOutcome}`).toBe(2);
                expect(accepted.length, `attempt ${attempt}: both nodes ran the check at the same time, so the active-check set is not shared: ${lastOutcome}`).toBeGreaterThan(0);

                if (refused.length === 1) {
                    refusal = lastOutcome;
                }
            }

            expect(refusal, `neither node ever refused the other, so the run always finished too quickly to tell: ${lastOutcome}`).toBeDefined();
        } finally {
            await startingNode.dispose();
            await competingNode.dispose();
        }
    });

    test("An LTI platform registered on one node is served by the other node's JWKS", async ({ playwright }) => {
        // Registering a platform generates its signing key and stores it in a distributed map. Every node publishes the
        // whole map as its JWKS, so a platform registered anywhere has to be verifiable everywhere - a per-node map
        // would leave the platform's tokens rejected by every node but the one that happened to register it.
        const readingNode = await playwright.request.newContext({ baseURL: NODE_URLS[1], ignoreHTTPSErrors: true });
        const ltiEnabled = (await activeModuleFeatures(readingNode)).includes('lti');
        if (!ltiEnabled) {
            await readingNode.dispose();
        }
        test.skip(!ltiEnabled, 'needs a deployment with the LTI module enabled');

        const adminNode = await contextFor(playwright.request, NODE_URLS[0], admin);
        const keysBefore = await publishedKeyIds(readingNode);
        let platformId: number | undefined;
        try {
            const suffix = generateUUID();
            const created = await adminNode.post('api/lti/admin/lti-platforms', {
                data: {
                    clientId: `client-${suffix}`,
                    customName: `Coherence platform ${suffix}`,
                    authorizationUri: 'https://platform.invalid/auth',
                    jwkSetUri: 'https://platform.invalid/jwks',
                    tokenUri: 'https://platform.invalid/token',
                },
            });
            expect(created.status(), `registering the platform on ${NODE_URLS[0]}: ${await bodyOf(created)}`).toBe(200);
            platformId = (await created.json()).id;

            await expect
                .poll(async () => (await publishedKeyIds(readingNode)).filter((keyId) => !keysBefore.includes(keyId)).length, {
                    message: `${NODE_URLS[1]} does not publish the key of a platform registered on ${NODE_URLS[0]}`,
                    timeout: 15_000,
                    intervals: [250, 500, 1_000],
                })
                .toBe(1);
        } finally {
            if (platformId !== undefined) {
                const deleted = await adminNode.delete(`api/lti/admin/lti-platforms/${platformId}`);
                expect(deleted.status(), `removing the platform again: ${await bodyOf(deleted)}`).toBe(200);
                // Removing the platform drops its key, and that has to reach the other node just as the write did.
                await expect.poll(() => publishedKeyIds(readingNode), { timeout: 15_000, intervals: [250, 500, 1_000] }).toEqual(keysBefore);
            }
            await adminNode.dispose();
            await readingNode.dispose();
        }
    });

    test('A feature switched off on one node is switched off on the other', async ({ playwright }) => {
        // Feature toggles live in a distributed map. Flipping one has to be visible on every node immediately —
        // otherwise a deployment answers the same request differently depending on which node the load balancer picked.
        const adminNode = await contextFor(playwright.request, NODE_URLS[0], admin);
        const otherNode = await contextFor(playwright.request, NODE_URLS[1], instructor);
        try {
            expect(await enabledFeatures(otherNode), 'plagiarism checks should be enabled before the test flips them').toContain('PlagiarismChecks');

            const disable = await adminNode.put('api/admin/feature-toggle', { data: { PlagiarismChecks: false } });
            expect(disable.status(), `disabling the feature on ${NODE_URLS[0]}: ${await bodyOf(disable)}`).toBe(200);

            await expect
                .poll(() => enabledFeatures(otherNode), {
                    message: `${NODE_URLS[1]} still reports the feature as enabled, so the toggle did not reach it`,
                    timeout: 15_000,
                    intervals: [250, 500, 1_000],
                })
                .not.toContain('PlagiarismChecks');

            // The other node must not only know about the toggle, it must also act on it.
            const refused = await otherNode.get('api/text/text-exercises/0/check-plagiarism?similarityThreshold=0&minimumScore=0&minimumSize=0');
            expect(refused.status(), `${NODE_URLS[1]} should refuse a disabled feature: ${await bodyOf(refused)}`).toBe(403);
        } finally {
            const restore = await adminNode.put('api/admin/feature-toggle', { data: { PlagiarismChecks: true } });
            expect(restore.status(), `restoring the feature on ${NODE_URLS[0]}: ${await bodyOf(restore)}`).toBe(200);
            await expect.poll(() => enabledFeatures(otherNode), { timeout: 15_000, intervals: [250, 500, 1_000] }).toContain('PlagiarismChecks');
            await adminNode.dispose();
            await otherNode.dispose();
        }
    });
});

/**
 * Opens a request context bound to one specific node, authenticated as the given user. Each node is addressed
 * directly rather than through the load balancer.
 *
 * <p>
 * The token is carried as a bearer header rather than left to the cookie jar. Artemis marks its JWT cookie `Secure`,
 * and a jar only keeps a `Secure` cookie over plain HTTP for `localhost`. The host-JVM runner reaches nodes on
 * `http://localhost:<port>` and would get away with it, but the Docker runner addresses them by container hostname,
 * where the cookie is dropped and every authenticated request comes back 401. Logging in through a throwaway context
 * keeps the jar empty afterwards, which matters because the server rejects a request carrying both a cookie and a
 * bearer token.
 *
 * @param apiRequest  the Playwright request factory
 * @param nodeUrl     the base URL of the node to talk to
 * @param credentials the user to log in as
 * @return a request context whose requests all go to that node
 */
async function contextFor(apiRequest: APIRequest, nodeUrl: string, credentials: { username: string; password: string }): Promise<APIRequestContext> {
    const loginContext = await apiRequest.newContext({ baseURL: nodeUrl, ignoreHTTPSErrors: true });
    try {
        const response = await loginContext.post('api/core/public/authenticate', {
            data: { username: credentials.username, password: credentials.password, rememberMe: true },
        });
        expect(response.status(), `login of ${credentials.username} on ${nodeUrl}`).toBe(200);
        const token = jwtFrom(response.headersArray());
        expect(token, `login of ${credentials.username} on ${nodeUrl} should hand out a token`).toBeDefined();
        return await apiRequest.newContext({ baseURL: nodeUrl, ignoreHTTPSErrors: true, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
    } finally {
        await loginContext.dispose();
    }
}

/**
 * @param headers the response headers of a successful authentication
 * @return the JWT the server handed out, read straight off the Set-Cookie header rather than out of the cookie jar
 */
function jwtFrom(headers: { name: string; value: string }[]): string | undefined {
    return headers
        .filter((header) => header.name.toLowerCase() === 'set-cookie')
        .map((header) => /(?:^|[;,\s])jwt=([^;]+)/.exec(header.value)?.[1])
        .find((token) => token !== undefined);
}

/**
 * @param response any response
 * @return its body as text, shortened so it stays readable inside an assertion message
 */
async function bodyOf(response: { text(): Promise<string> }): Promise<string> {
    const text = await response.text();
    return text.length > 300 ? `${text.slice(0, 300)}…` : text;
}

/**
 * Reads the feature toggles a node currently applies. /management/info is public and reports them straight from the
 * distributed map, so it shows that node's own view rather than the one of whoever wrote the toggle.
 *
 * @param context a request context bound to one node
 * @return the names of the features that node considers enabled
 */
async function enabledFeatures(context: APIRequestContext): Promise<string[]> {
    const response = await context.get('management/info');
    expect(response.status(), 'reading the enabled features').toBe(200);
    return (await response.json()).features ?? [];
}

/**
 * @param context a request context bound to one node
 * @return the module features that node runs with
 */
async function activeModuleFeatures(context: APIRequestContext): Promise<string[]> {
    const response = await context.get('management/info');
    expect(response.status(), 'reading the active module features').toBe(200);
    return (await response.json()).activeModuleFeatures ?? [];
}

/**
 * @param context a request context bound to one node
 * @return the key ids that node publishes in its LTI JWKS, sorted so two nodes can be compared directly
 */
async function publishedKeyIds(context: APIRequestContext): Promise<string[]> {
    const response = await context.get('.well-known/jwks.json');
    expect(response.status(), 'reading the JWKS').toBe(200);
    return ((await response.json()).keys ?? []).map((key: { kid: string }) => key.kid).sort();
}

/**
 * Uploads a file for the given exercise, optionally replacing the file of an existing submission.
 *
 * @param context      a request context bound to one node
 * @param exerciseId   the file upload exercise
 * @param submissionId the submission to replace the file of, or undefined to create one
 * @param submitted    whether the submission counts as handed in
 * @param content      the file content to store
 * @return the path the server stored the file under
 */
async function submitFile(context: APIRequestContext, exerciseId: number, submissionId: number | undefined, submitted: boolean, content: string): Promise<string> {
    const response = await context.post(`api/fileupload/exercises/${exerciseId}/file-upload-submissions`, {
        multipart: {
            // The file name has to stay the same across both uploads: the stored path is derived from it, and only an
            // unchanged path can hold a stale cache entry. Its extension has to match the exercise's file pattern,
            // which the shipped template sets to pdf.
            file: { name: 'submission.pdf', mimeType: 'application/pdf', buffer: Buffer.from(content) },
            submission: { name: 'submission', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ id: submissionId, submitted })) },
        },
    });
    expect(response.status(), `file upload submission: ${await bodyOf(response)}`).toBe(200);
    return (await response.json()).filePath;
}

/**
 * The stored path carries the submission it belongs to, which is the only reliable way to name it here: a participation
 * can hold several submissions and their order in the exercise details is not the upload order.
 *
 * @param filePath a stored path as returned by the submission endpoint
 * @return the id of the submission that path belongs to
 */
function submissionIdFromPath(filePath: string): number {
    const match = /\/submissions\/(\d+)\//.exec(filePath);
    expect(match, `the stored path should name its submission: ${filePath}`).not.toBeNull();
    return Number(match![1]);
}

/**
 * @param context a request context bound to one node
 * @param filePath the stored path as returned by the submission endpoint
 * @return the file content that node serves
 */
async function readFile(context: APIRequestContext, filePath: string): Promise<string> {
    const response = await context.get(`api/core/files/${filePath}`);
    expect(response.status(), `reading ${filePath}`).toBe(200);
    return (await response.body()).toString();
}
