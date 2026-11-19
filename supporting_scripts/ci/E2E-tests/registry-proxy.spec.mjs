import { afterEach, describe, expect, it } from 'vitest';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('../pull-through-registry-proxy.sh', import.meta.url));
const proxy = 'registry.example/hub-cache';
const directories = [];
afterEach(() => {
    for (const directory of directories.splice(0)) rmSync(directory, { recursive: true, force: true });
});

/**
 * Runs the helper against a stub `docker` that records its calls.
 *
 * @param images the images to make available
 * @param options `present` lists images that already exist, `failing` lists references whose pull fails
 */
function run(images, { present = [], failing = [], bestEffort = false, env = {} } = {}) {
    const directory = mkdtempSync(join(tmpdir(), 'artemis registry proxy '));
    directories.push(directory);
    const calls = join(directory, 'docker-calls');
    writeFileSync(calls, '');
    const matches = (references) => references.map((reference) => `"${reference}"`).join('|') || '""';
    writeFileSync(
        join(directory, 'docker'),
        `#!/bin/sh
echo "$*" >> "$TEST_DOCKER_CALLS"
case "$1" in
  image) case "$3" in ${matches(present)}) exit 0 ;; *) exit 1 ;; esac ;;
  pull) for last; do :; done; case "$last" in ${matches(failing)}) exit 1 ;; *) exit 0 ;; esac ;;
esac
exit 0
`,
        { mode: 0o700 },
    );
    // `timeout` is replaced by a pass-through, so the stub decides the outcome of a pull on every platform.
    writeFileSync(join(directory, 'timeout'), '#!/bin/sh\nshift 2\nexec "$@"\n', { mode: 0o700 });
    const result = spawnSync('bash', [script, ...(bestEffort ? ['--best-effort'] : []), ...images], {
        encoding: 'utf8',
        env: { ...process.env, PATH: `${directory}:${process.env.PATH}`, TEST_DOCKER_CALLS: calls, DOCKER_HUB_PROXY: proxy, ...env },
    });
    return { ...result, calls: readFileSync(calls, 'utf8').trim().split('\n').filter(Boolean) };
}

describe('pull-through-registry-proxy.sh', () => {
    it('leaves an image alone that is already present', () => {
        const { status, calls } = run(['alpine:3'], { present: ['alpine:3'] });
        expect(status).toBe(0);
        expect(calls).toEqual(['image inspect alpine:3']);
    });

    it.each([
        ['alpine:3', 'library/alpine:3'],
        ['docker.io/library/postgres:18', 'library/postgres:18'],
        ['docker.io/nginx:1', 'library/nginx:1'],
        ['ls1tum/artemis-maven-template:java25-5', 'ls1tum/artemis-maven-template:java25-5'],
        ['index.docker.io/valkey/valkey:9', 'valkey/valkey:9'],
    ])('pulls the Docker Hub image %s through the proxy and gives it its own name', (image, path) => {
        const { status, calls } = run([image]);
        expect(status).toBe(0);
        expect(calls).toEqual([`image inspect ${image}`, `pull --quiet ${proxy}/${path}`, `tag ${proxy}/${path} ${image}`, `rmi ${proxy}/${path}`]);
    });

    it.each(['ghcr.io/ls1intum/artemis:latest', 'quay.io/keycloak/keycloak:26', 'localhost:5000/image:1', 'cr.weaviate.io/semitechnologies/weaviate:1'])(
        'pulls %s from its own registry',
        (image) => {
            const { status, calls } = run([image]);
            expect(status).toBe(0);
            expect(calls).toEqual([`image inspect ${image}`, `pull --quiet ${image}`]);
        },
    );

    it('falls back to Docker Hub when the proxy cannot serve the image', () => {
        const { status, calls, stdout } = run(['alpine:3'], { failing: [`${proxy}/library/alpine:3`] });
        expect(status).toBe(0);
        expect(calls).toEqual(['image inspect alpine:3', `pull --quiet ${proxy}/library/alpine:3`, 'pull --quiet alpine:3']);
        expect(stdout).toContain('::warning title=Registry proxy unavailable::');
    });

    it('pulls from Docker Hub only when no proxy is configured', () => {
        const { status, calls } = run(['alpine:3'], { env: { DOCKER_HUB_PROXY: '' } });
        expect(status).toBe(0);
        expect(calls).toEqual(['image inspect alpine:3', 'pull --quiet alpine:3']);
    });

    it('fails when an image is available nowhere, after trying the remaining images', () => {
        const { status, stdout, calls } = run(['alpine:3', 'busybox:1'], { failing: [`${proxy}/library/alpine:3`, 'alpine:3'] });
        expect(status).toBe(1);
        expect(stdout).toContain('::error title=Could not pull images::alpine:3');
        expect(calls).toContain(`tag ${proxy}/library/busybox:1 busybox:1`);
    });

    it('only warns about an unavailable image in best-effort mode', () => {
        const { status, stdout } = run(['alpine:3'], { failing: [`${proxy}/library/alpine:3`, 'alpine:3'], bestEffort: true });
        expect(status).toBe(0);
        expect(stdout).toContain('::warning title=Could not pull images::alpine:3');
    });

    it('explains its usage when called without an image', () => {
        expect(run([]).status).toBe(2);
    });
});
