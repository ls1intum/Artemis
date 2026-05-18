import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { request } from '@playwright/test';
import { SSH_KEYS_PATH, SSH_KEY_NAMES } from '../support/pageobjects/exercises/programming/GitClient';
import { admin, studentOne, studentTwo, studentThree, studentFour, tutor, instructor, UserCredentials } from '../support/users';
import { SEED_COURSES } from '../support/seedData';

const AUTH_DIR = path.join(__dirname, '..', '.auth');
const JWT_TOKENS_PATH = path.join(AUTH_DIR, 'jwt-tokens.json');

async function globalSetup() {
    console.log('Running global setup...');

    // Set correct permissions to the SSH keys
    try {
        for (const keyName of Object.values(SSH_KEY_NAMES)) {
            const privateKeyPath = path.join(SSH_KEYS_PATH, keyName);
            const publicKeyPath = `${privateKeyPath}.pub`;

            fs.chmodSync(privateKeyPath, 0o600);
            fs.chmodSync(publicKeyPath, 0o644);
        }
    } catch (error) {
        console.error('Error during SSH key setup:', error);
    }

    // Pre-authenticate all users and cache JWT tokens
    const adminJwt = await preAuthenticateUsers();

    // Warm up backend caches (JIT, Hibernate, Hazelcast, connection pools) by hitting a curated
    // set of slow read endpoints in parallel. Eliminates the 30-90s cold-start tail that
    // otherwise causes the first test of each feature area to hang on its initial waitForResponse.
    if (adminJwt) {
        await prewarmBackend(adminJwt);
    }

    // Clean up accumulated test data (group chats, etc.) to prevent limit errors
    cleanupAccumulatedTestData();

    console.log('Global setup completed.');
}

function cleanupAccumulatedTestData() {
    try {
        // Clean up accumulated group chats from previous test runs via PostgreSQL.
        // Group chats have no delete API, so they accumulate and hit the 50 per-user-per-course limit.
        const sql = `
            DELETE FROM conversation_participant WHERE conversation_id IN (
                SELECT id FROM conversation WHERE discriminator = 'G' AND course_id IN (9003, 9004)
            );
            DELETE FROM post WHERE conversation_id IN (
                SELECT id FROM conversation WHERE discriminator = 'G' AND course_id IN (9003, 9004)
            );
            DELETE FROM conversation WHERE discriminator = 'G' AND course_id IN (9003, 9004);
        `;
        const result = execSync(`docker exec artemis-postgres psql -U Artemis -d Artemis -c "${sql.replace(/\n/g, ' ')}"`, {
            timeout: 10000,
            encoding: 'utf-8',
        });
        console.log('Cleaned up accumulated group chats:', result.trim());
    } catch (error) {
        // Docker/DB may not be available (e.g., CI with different setup) — skip silently
        console.log('Skipping DB cleanup (docker exec not available)');
    }
}

async function preAuthenticateUsers(): Promise<string | undefined> {
    const baseURL = process.env.BASE_URL ?? 'http://localhost:9000';
    const allUsers: UserCredentials[] = [admin, studentOne, studentTwo, studentThree, studentFour, tutor, instructor];

    const tokens: Record<string, { value: string; expires: number }> = {};

    console.log(`Pre-authenticating ${allUsers.length} users against ${baseURL}...`);

    for (const user of allUsers) {
        // Use a fresh context per user to avoid cookie contamination
        const context = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
        try {
            const response = await context.post('api/core/public/authenticate', {
                data: { username: user.username, password: user.password, rememberMe: true },
            });

            if (response.ok()) {
                // Extract JWT cookie from the response
                const setCookieHeaders = response.headers()['set-cookie'];
                if (setCookieHeaders) {
                    const jwtMatch = setCookieHeaders.match(/jwt=([^;]+)/);
                    if (jwtMatch) {
                        tokens[user.username] = {
                            value: jwtMatch[1],
                            expires: Date.now() + 30 * 24 * 60 * 60 * 1000, // 30 days
                        };
                        console.log(`  Authenticated: ${user.username}`);
                        continue;
                    }
                }

                // Fallback: try to get cookies from the context
                const cookies = await context.storageState();
                const jwtCookie = cookies.cookies.find((c) => c.name === 'jwt');
                if (jwtCookie) {
                    tokens[user.username] = {
                        value: jwtCookie.value,
                        expires: jwtCookie.expires * 1000,
                    };
                    console.log(`  Authenticated: ${user.username} (via cookie store)`);
                } else {
                    console.warn(`  Warning: No JWT cookie found for ${user.username}`);
                }
            } else {
                console.warn(`  Warning: Auth failed for ${user.username}: ${response.status()}`);
            }
        } catch (error) {
            console.warn(`  Warning: Could not authenticate ${user.username}: ${error}`);
        } finally {
            await context.dispose();
        }
    }

    // Save tokens to disk
    fs.mkdirSync(AUTH_DIR, { recursive: true });
    fs.writeFileSync(JWT_TOKENS_PATH, JSON.stringify(tokens, undefined, 2));
    console.log(`Saved ${Object.keys(tokens).length} JWT tokens to ${JWT_TOKENS_PATH}`);

    return tokens[admin.username]?.value;
}

/**
 * Pre-warm a curated set of read-only backend endpoints with the admin JWT to force
 * JIT compilation of the Spring controller chain, populate Hibernate query plans and
 * second-level caches, initialize Hazelcast partition ownership, and fill the JDBC
 * connection pool. All seed IDs come from `support/seedData.ts` so a single source of
 * truth defines what the warmer touches.
 *
 * The flow:
 *   1. Strict health gate — one `GET /management/health` must return 200, otherwise
 *      we throw. This separates "backend down" (real outage, fail loud) from "endpoint
 *      slow" (warm-up, fail soft).
 *   2. Soft warm pass — fire ~12 GETs in parallel. Each result is logged but never
 *      throws; a 404 because seed-data drifted or a 503 because one module is still
 *      starting up is acceptable.
 */
async function prewarmBackend(adminJwt: string): Promise<void> {
    const baseURL = process.env.BASE_URL ?? 'http://localhost:9000';
    const ctx = await request.newContext({
        baseURL,
        ignoreHTTPSErrors: true,
        extraHTTPHeaders: { cookie: `jwt=${adminJwt}` },
    });
    try {
        // Strict health gate
        const healthStart = Date.now();
        const healthResp = await ctx.get('/management/health', { timeout: 30_000 });
        if (!healthResp.ok()) {
            throw new Error(`[prewarm] backend health check failed: HTTP ${healthResp.status()}`);
        }
        console.log(`[prewarm] health OK (${Date.now() - healthStart} ms)`);

        // Soft warm pass — read endpoints against seed entities, no side effects
        const atlas = SEED_COURSES.atlas1.id;
        const examMgmt = SEED_COURSES.examManagement.id;
        const programmingMgmt = SEED_COURSES.programmingManagement.id;
        const quizPart = SEED_COURSES.quizParticipation.id;
        const channel1 = SEED_COURSES.channel1.id;
        const exerciseMgmt = SEED_COURSES.exerciseManagement.id;
        const lectureMgmt = SEED_COURSES.lectureManagement.id;
        const endpoints = [
            'api/core/courses/course-management-overview',
            `api/core/courses/${atlas}/with-exercises-lectures-competencies`,
            `api/atlas/courses/${atlas}/course-competencies`,
            `api/atlas/courses/${atlas}/learning-paths`,
            `api/exam/courses/${examMgmt}/exams`,
            `api/programming/courses/${programmingMgmt}/programming-exercises`,
            `api/quiz/courses/${quizPart}/quiz-exercises`,
            `api/text/courses/${exerciseMgmt}/text-exercises`,
            `api/modeling/courses/${exerciseMgmt}/modeling-exercises`,
            `api/lecture/courses/${lectureMgmt}/lectures`,
            `api/communication/courses/${channel1}/posts`,
            `api/communication/courses/${channel1}/messages`,
        ];

        console.log(`[prewarm] warming ${endpoints.length} endpoints in parallel...`);
        const warmStart = Date.now();
        await Promise.allSettled(
            endpoints.map(async (url) => {
                const t0 = Date.now();
                try {
                    const resp = await ctx.get(url, { timeout: 60_000 });
                    const ms = Date.now() - t0;
                    if (resp.ok()) {
                        console.log(`[prewarm]   ${ms.toString().padStart(5)} ms  ${url}`);
                    } else {
                        console.log(`[prewarm]   skip (${resp.status()}) ${url}`);
                    }
                } catch (error) {
                    console.log(`[prewarm]   error ${url}: ${error}`);
                }
            }),
        );
        console.log(`[prewarm] warm pass finished in ${Date.now() - warmStart} ms`);
    } finally {
        await ctx.dispose();
    }
}

export default globalSetup;
