import { describe, expect, it } from 'vitest';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

function checkStorage(availableKiB, totalKiB = 100 * 1024 * 1024, pruneStatus = 0, { afterCacheKiB = availableKiB, cacheStatus = 0 } = {}) {
    const directory = mkdtempSync(path.join(tmpdir(), 'artemis-e2e-storage-'));
    const calls = path.join(directory, 'calls');
    try {
        writeFileSync(calls, '');
        writeFileSync(
            path.join(directory, 'docker'),
            `#!/bin/sh
printf '%s\\n' "$*" >> "$TEST_CALLS"
case "$1 $2" in
  'image prune') exit "$TEST_PRUNE_STATUS" ;;
  'buildx prune') touch "$TEST_CACHE_MARKER"; exit "$TEST_CACHE_STATUS" ;;
  'info --format') printf '/docker-data\\n' ;;
  'system df') exit 0 ;;
  *) exit 91 ;;
esac
`,
            { mode: 0o700 },
        );
        writeFileSync(
            path.join(directory, 'df'),
            `#!/bin/sh
[ "$1" = '-Pk' ] && [ "$2" = '/docker-data' ] || exit 92
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\\n'
[ ! -f "$TEST_CACHE_MARKER" ] || TEST_AVAILABLE="$TEST_AFTER_CACHE"
printf '/dev/test %s 0 %s 0%% /docker-data\\n' "$TEST_TOTAL" "$TEST_AVAILABLE"
`,
            { mode: 0o700 },
        );
        const result = spawnSync('bash', ['supporting_scripts/ci/E2E-tests/prepare-docker-storage.sh'], {
            encoding: 'utf8',
            env: {
                ...process.env,
                PATH: `${directory}:${process.env.PATH}`,
                TEST_CALLS: calls,
                TEST_PRUNE_STATUS: String(pruneStatus),
                TEST_TOTAL: String(totalKiB),
                TEST_AVAILABLE: String(availableKiB),
                TEST_AFTER_CACHE: String(afterCacheKiB),
                TEST_CACHE_STATUS: String(cacheStatus),
                TEST_CACHE_MARKER: path.join(directory, 'cache-pruned'),
            },
        });
        return { ...result, calls: readFileSync(calls, 'utf8') };
    } finally {
        rmSync(directory, { recursive: true, force: true });
    }
}

describe('E2E Docker storage preparation', () => {
    it('reclaims only unused Artemis images older than one day and leaves room for the suite', () => {
        const result = checkStorage(25 * 1024 * 1024);
        expect(result.status).toBe(0);
        expect(result.calls).toContain('image prune --all --force --filter label=org.opencontainers.image.source=https://github.com/ls1intum/Artemis --filter until=24h');
        expect(result.calls).not.toContain('system prune');
        expect(result.calls).not.toContain('buildx prune');
    });

    it('reports disk pressure as setup failure before broker backpressure causes test timeouts', () => {
        const result = checkStorage(19 * 1024 * 1024);
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/insufficient Docker disk headroom/i);
        expect(result.calls).toContain('system df');
    });

    it('recovers low headroom by trimming unused build cache and checking the filesystem again', () => {
        const result = checkStorage(19 * 1024 * 1024, undefined, undefined, { afterCacheKiB: 21 * 1024 * 1024 });
        expect(result.status).toBe(0);
        expect(result.calls).toContain('buildx prune --builder default --force --max-used-space 2GB --reserved-space 2GB');
        expect(result.calls).not.toContain('system df');
        expect(result.stdout).toContain(`${21 * 1024 * 1024} KiB available`);
    });

    it('attempts cache cleanup once before reporting persistent disk pressure', () => {
        const result = checkStorage(19 * 1024 * 1024);
        expect(result.status).not.toBe(0);
        expect(result.calls.match(/buildx prune/g)).toHaveLength(1);
        expect(result.calls).toContain('system df');
    });

    it('fails closed when build cache cleanup fails', () => {
        const result = checkStorage(19 * 1024 * 1024, undefined, undefined, { afterCacheKiB: 21 * 1024 * 1024, cacheStatus: 1 });
        expect(result.status).not.toBe(0);
        expect(result.calls).toContain('buildx prune');
    });

    it('rejects invalid filesystem statistics after cache cleanup', () => {
        const result = checkStorage(19 * 1024 * 1024, undefined, undefined, { afterCacheKiB: 'unavailable' });
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/cannot determine Docker disk headroom/i);
    });

    it('accounts for the broker percentage threshold on larger runner disks', () => {
        expect(checkStorage(50 * 1024 * 1024, 1000 * 1024 * 1024).status).not.toBe(0);
    });

    it('does not hide a failed image cleanup', () => {
        expect(checkStorage(25 * 1024 * 1024, undefined, 1).status).not.toBe(0);
    });

    it('fails closed when filesystem statistics are unavailable', () => {
        const result = checkStorage('unavailable');
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/cannot determine Docker disk headroom/i);
    });
});
