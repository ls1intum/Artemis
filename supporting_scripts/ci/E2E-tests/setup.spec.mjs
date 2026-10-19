import { afterEach, describe, expect, it, vi } from 'vitest';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { waitForServerReadiness } from '../../../src/test/playwright/init/server-readiness.ts';

// These cases catch starting authentication while a second core node is still booting,
// accepting an HTML/root response as readiness, and testing an image from another commit.
afterEach(() => vi.useRealTimers());

describe('server readiness before authentication', () => {
    it('waits for delayed core nodes as well as the load balancer', async () => {
        vi.useFakeTimers();
        const requests = [];
        const context = {
            get: async (url) => {
                requests.push(url);
                const ready = !url.includes('node-2') || Date.now() >= 2000;
                return { status: () => (ready ? 200 : 503), json: async () => ({ status: ready ? 'UP' : 'DOWN' }) };
            },
        };
        vi.setSystemTime(0);
        let completed = false;
        const result = waitForServerReadiness(context, 'https://load-balancer', 'http://node-1:8080,http://node-2:8080', { timeoutMs: 3000, pollIntervalMs: 1000 }).then(() => {
            completed = true;
        });
        await vi.advanceTimersByTimeAsync(1000);
        expect(completed).toBe(false);
        await vi.advanceTimersByTimeAsync(1000);
        await result;
        expect(completed).toBe(true);
        expect(new Set(requests)).toEqual(
            new Set(['https://load-balancer/management/health/readiness', 'http://node-1:8080/management/health/readiness', 'http://node-2:8080/management/health/readiness']),
        );
    });

    it('fails within the readiness budget and names every unavailable node', async () => {
        vi.useFakeTimers();
        const context = {
            get: async (url) => {
                if (url.includes('node-1')) throw new Error('connection refused');
                return { status: () => 503, json: async () => ({ status: 'DOWN' }) };
            },
        };
        const result = waitForServerReadiness(context, 'http://load-balancer', 'http://node-1:8080,http://node-2:8080', { timeoutMs: 2000, pollIntervalMs: 1000 });
        const assertion = expect(result).rejects.toThrow(/node-1:8080.*request failed[\s\S]*node-2:8080.*HTTP 503/);
        await vi.advanceTimersByTimeAsync(2000);
        await assertion;
    });

    it('rejects a 200 response without UP readiness state', async () => {
        vi.useFakeTimers();
        const context = { get: async () => ({ status: () => 200, json: async () => ({ status: 'DOWN' }) }) };
        const result = waitForServerReadiness(context, 'http://node-1:8080', undefined, { timeoutMs: 1000, pollIntervalMs: 500 });
        const assertion = expect(result).rejects.toThrow(/node-1:8080.*readiness status DOWN/);
        await vi.advanceTimersByTimeAsync(1000);
        await assertion;
    });
});

describe('CI image revision gate', () => {
    const expected = 'a'.repeat(40);
    function verify(revision, tag = `sha-${expected}`, commit = expected) {
        const directory = mkdtempSync(path.join(tmpdir(), 'artemis-image-gate-'));
        try {
            writeFileSync(
                path.join(directory, 'docker'),
                `#!/bin/sh
[ "$1" = image ] && [ "$2" = inspect ] && [ "$3" = --format ] || exit 91
[ "$4" = '{{ index .Config.Labels "org.opencontainers.image.revision" }}' ] || exit 92
[ "$5" = "ghcr.io/ls1intum/artemis:sha-$ARTEMIS_EXPECTED_COMMIT_SHA" ] || exit 93
printf "%s" "$TEST_REVISION"
`,
                { mode: 0o700 },
            );
            return spawnSync('bash', ['supporting_scripts/ci/E2E-tests/verify-image-revision.sh'], {
                encoding: 'utf8',
                env: { ...process.env, PATH: `${directory}:${process.env.PATH}`, TEST_REVISION: revision, ARTEMIS_EXPECTED_COMMIT_SHA: commit, ARTEMIS_DOCKER_TAG: tag },
            });
        } finally {
            rmSync(directory, { recursive: true, force: true });
        }
    }
    it('allows the built revision', () => expect(verify(expected).status).toBe(0));
    it('requires an expected commit in CI', () => {
        const result = verify(expected, `sha-${expected}`, '');
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/must be a full commit SHA/);
    });
    it('fails closed for another revision', () => {
        const result = verify('b'.repeat(40));
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/revision mismatch/);
    });
    it('fails closed when the OCI revision is absent', () => {
        const result = verify('<no value>');
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/revision mismatch/);
    });
    it('rejects a mutable tag before pulling an unrelated image', () => {
        const result = verify(expected, 'develop');
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/expected.*sha-/);
    });
});
