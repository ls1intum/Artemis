import { describe, expect, it } from 'vitest';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

function checkStorage(
    availableKiB,
    totalKiB = 100 * 1024 * 1024,
    pruneStatus = 0,
    { afterCacheKiB = availableKiB, cacheStatus = 0, afterImagesKiB = afterCacheKiB, imageStatus = 0 } = {},
) {
    const directory = mkdtempSync(path.join(tmpdir(), 'artemis-e2e-storage-'));
    const calls = path.join(directory, 'calls');
    try {
        writeFileSync(calls, '');
        writeFileSync(
            path.join(directory, 'docker'),
            `#!/bin/sh
printf '%s\\n' "$*" >> "$TEST_CALLS"
case "$1 $2" in
  'image prune')
    case "$*" in
      *until=24h*) exit "$TEST_PRUNE_STATUS" ;;
      *) touch "$TEST_IMAGES_MARKER"; exit "$TEST_IMAGE_STATUS" ;;
    esac ;;
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
[ ! -f "$TEST_IMAGES_MARKER" ] || TEST_AVAILABLE="$TEST_AFTER_IMAGES"
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
                TEST_AFTER_IMAGES: String(afterImagesKiB),
                TEST_IMAGE_STATUS: String(imageStatus),
                TEST_IMAGES_MARKER: path.join(directory, 'images-pruned'),
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
        expect(result.calls.match(/image prune/g)).toHaveLength(1);
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
        expect(result.calls).toContain('buildx prune --builder default --force --min-free-space 21474836480 --reserved-space 512MB');
        expect(result.calls).not.toContain('system df');
        expect(result.calls.match(/image prune/g)).toHaveLength(1);
        expect(result.stdout).toContain(`${21 * 1024 * 1024} KiB available`);
    });

    it('targets the filesystem reserve rather than a fixed cache budget on larger disks', () => {
        const result = checkStorage(109 * 1024 * 1024, 1000 * 1024 * 1024, undefined, { afterCacheKiB: 111 * 1024 * 1024 });
        expect(result.status).toBe(0);
        expect(result.calls).toContain('buildx prune --builder default --force --min-free-space 118111600640 --reserved-space 512MB');
    });

    it('recovers the develop runner shortfall by dropping only unused Artemis image retention after cache cleanup', () => {
        const result = checkStorage(18_200_000, 97_465_650, undefined, { afterCacheKiB: 18_726_380, afterImagesKiB: 23_000_000 });
        expect(result.status).toBe(0);
        const calls = result.calls.trim().split('\n');
        const pressurePrune = 'image prune --all --force --filter label=org.opencontainers.image.source=https://github.com/ls1intum/Artemis';
        expect(calls).toContain(pressurePrune);
        expect(calls.indexOf(pressurePrune)).toBeGreaterThan(calls.findIndex((call) => call.startsWith('buildx prune')));
        expect(calls.filter((call) => call.startsWith('image prune'))).toHaveLength(2);
        expect(result.calls).not.toContain('system prune');
        expect(result.calls).not.toContain('system df');
        expect(result.stdout).toContain('23000000 KiB available, 20232325 KiB required');
    });

    it('fails closed when unused application image cleanup fails', () => {
        const result = checkStorage(19 * 1024 * 1024, undefined, undefined, { afterImagesKiB: 21 * 1024 * 1024, imageStatus: 1 });
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/cannot reclaim unused Artemis images/i);
    });

    it('rejects invalid filesystem statistics after unused application image cleanup', () => {
        const result = checkStorage(19 * 1024 * 1024, undefined, undefined, { afterImagesKiB: 'unavailable' });
        expect(result.status).not.toBe(0);
        expect(result.stdout + result.stderr).toMatch(/cannot determine Docker disk headroom/i);
    });

    it('attempts each cleanup once before reporting persistent disk pressure', () => {
        const result = checkStorage(19 * 1024 * 1024);
        expect(result.status).not.toBe(0);
        expect(result.calls.match(/buildx prune/g)).toHaveLength(1);
        expect(result.calls.match(/image prune/g)).toHaveLength(2);
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
