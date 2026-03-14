import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { request } from '@playwright/test';
import { SSH_KEYS_PATH, SSH_KEY_NAMES } from '../support/pageobjects/exercises/programming/GitClient';
import { admin, studentOne, studentTwo, studentThree, studentFour, tutor, instructor, UserCredentials } from '../support/users';

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
    await preAuthenticateUsers();

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

async function preAuthenticateUsers() {
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
}

export default globalSetup;
