import { afterEach, describe, expect, it } from 'vitest';
import { mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { parse } from 'yaml';

const workflow = parse(readFileSync(new URL('../../../.github/workflows/ci-test.yml', import.meta.url), 'utf8'));
const directories = [];
afterEach(() => {
    for (const directory of directories.splice(0)) rmSync(directory, { recursive: true, force: true });
});

for (const job of ['server-tests', 'server-tests-report', 'client-tests']) {
    describe(`${job} on a shared E2E runner`, () => {
        function recovery() {
            const steps = workflow.jobs[job].steps;
            const checkout = steps.findIndex((step) => step.uses?.startsWith('actions/checkout@'));
            const index = steps.findIndex((step) => step.name === 'Return ownership of leftover Playwright output');
            expect(index).toBeGreaterThanOrEqual(0);
            expect(index).toBeLessThan(checkout);
            expect(steps[index]['continue-on-error']).toBe(true);
            return steps[index].run;
        }

        const proxyImage = 'harbor.aet.cit.tum.de/dockerhub-ghr-proxy/library/alpine:3.24.4';

        function execute(script, outputs) {
            const directory = mkdtempSync(join(tmpdir(), 'artemis checkout recovery '));
            directories.push(directory);
            const calls = join(directory, 'docker-calls');
            writeFileSync(calls, '');
            writeFileSync(join(directory, 'docker'), `#!/bin/sh\nprintf '%s\\n' "$@" >> "$TEST_DOCKER_CALLS"\n`, { mode: 0o700 });
            for (const output of outputs) mkdirSync(join(directory, 'src/test/playwright', output), { recursive: true });
            const result = spawnSync('bash', ['-e', '-c', script], {
                cwd: directory,
                encoding: 'utf8',
                env: { ...process.env, PATH: `${directory}:${process.env.PATH}`, TEST_DOCKER_CALLS: calls },
            });
            expect(result.status, result.stderr).toBe(0);
            return { directory: realpathSync(directory), calls: readFileSync(calls, 'utf8').trim().split('\n').filter(Boolean) };
        }

        it('restores only leftover Playwright output before checkout, preserving paths with spaces', () => {
            const outputs = ['test-results', 'test-reports'];
            const { directory, calls } = execute(recovery(), outputs);
            expect(calls).toEqual(
                outputs.flatMap((output) => [
                    'run',
                    '--rm',
                    '--user',
                    '0:0',
                    '--volume',
                    `${directory}/src/test/playwright/${output}:/output`,
                    proxyImage,
                    'chown',
                    '-R',
                    `${process.getuid()}:${process.getgid()}`,
                    '/output',
                ]),
            );
        });

        it('never pulls from Docker Hub itself', () => {
            expect(recovery()).not.toContain('docker.io');
        });

        it('does not need Docker when the output directories do not exist', () => {
            expect(execute(recovery(), []).calls).toEqual([]);
        });
    });
}
