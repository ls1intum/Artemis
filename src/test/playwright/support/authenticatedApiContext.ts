import { APIRequest, APIRequestContext } from '@playwright/test';
import { UserCredentials } from './users';

/** Authenticates a separate cookie jar so a page role switch cannot change setup or cleanup permissions. */
export async function createAuthenticatedApiContext(
    request: APIRequest,
    baseURL: string,
    credentials: UserCredentials,
    requiredAuthorities?: readonly string[],
): Promise<APIRequestContext> {
    const context = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
    try {
        const authentication = await context.post('api/core/public/authenticate', {
            data: { username: credentials.username, password: credentials.password, rememberMe: true },
        });
        if (!authentication.ok()) {
            throw new Error(`API authentication failed for ${credentials.username}: ${authentication.status()} ${await authentication.text()}`);
        }
        const response = await context.get('api/core/public/account');
        if (!response.ok()) {
            throw new Error(`API identity verification failed for ${credentials.username}: ${response.status()} ${await response.text()}`);
        }
        const account = (await response.json()) as { login?: string; authorities?: string[] };
        if (account.login !== credentials.username || (requiredAuthorities?.length && !requiredAuthorities.some((authority) => account.authorities?.includes(authority)))) {
            throw new Error(
                `API identity mismatch: expected ${credentials.username}${requiredAuthorities?.length ? ` with one of [${requiredAuthorities.join(', ')}]` : ''}, ` +
                    `received ${account.login} with [${account.authorities?.join(', ') ?? ''}]`,
            );
        }
        return context;
    } catch (error) {
        await context.dispose();
        throw error;
    }
}
