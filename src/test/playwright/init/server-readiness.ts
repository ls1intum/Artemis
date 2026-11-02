import type { APIRequestContext } from '@playwright/test';

interface ReadinessOptions {
    timeoutMs?: number;
    pollIntervalMs?: number;
}

/** Gate authentication on the load balancer and every directly addressable core node. */
export async function waitForServerReadiness(
    context: Pick<APIRequestContext, 'get'>,
    baseURL: string,
    multiNodeURLs?: string,
    { timeoutMs = 120_000, pollIntervalMs = 1000 }: ReadinessOptions = {},
): Promise<void> {
    const urls = [
        ...new Set(
            [
                baseURL,
                ...(multiNodeURLs
                    ?.split(',')
                    .map((url) => url.trim())
                    .filter(Boolean) ?? []),
            ].map((url) => new URL(url).origin),
        ),
    ];
    const deadline = Date.now() + timeoutMs;
    const results = await Promise.all(
        urls.map(async (url) => {
            let lastFailure = 'not checked';
            while (Date.now() < deadline) {
                try {
                    const response = await context.get(`${url}/management/health/readiness`, { timeout: Math.max(1, Math.min(5000, deadline - Date.now())) });
                    if (response.status() === 200) {
                        const health = await response.json();
                        if (health.status === 'UP') {
                            console.log(`[readiness] ${url}: UP`);
                            return undefined;
                        }
                        lastFailure = `readiness status ${health.status === 'DOWN' || health.status === 'OUT_OF_SERVICE' ? health.status : 'not UP'}`;
                    } else {
                        lastFailure = `HTTP ${response.status()}`;
                    }
                } catch {
                    // Do not print raw request errors: they can contain headers or credentials.
                    lastFailure = 'request failed (connection, timeout, or invalid readiness response)';
                }
                const remaining = deadline - Date.now();
                if (remaining > 0) {
                    await new Promise((resolve) => setTimeout(resolve, Math.min(pollIntervalMs, remaining)));
                }
            }
            return `${url}: ${lastFailure}`;
        }),
    );
    const failures = results.filter((failure) => failure !== undefined);
    if (failures.length) {
        throw new Error(`[readiness] Servers did not become ready within ${timeoutMs} ms:\n${failures.join('\n')}`);
    }
}
