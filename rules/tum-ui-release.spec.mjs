import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import process from 'node:process';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { parse } from 'yaml';

const root = resolve(import.meta.dirname, '..');
const workflow = parse(readFileSync(resolve(root, '.github/workflows/release-tum-ui.yml'), 'utf8'));
const validator = workflow.jobs.build.steps.find((step) => step.name === 'Validate release identity').run;

it('withholds publishing credentials until build and consumer checks pass', () => {
    expect(workflow.permissions).toEqual({});
    expect(
        Object.entries(workflow.jobs)
            .filter(([, job]) => job.permissions?.['id-token'] === 'write')
            .map(([name]) => name),
    ).toEqual(['stage']);
    expect(workflow.jobs.stage.needs).toEqual(expect.arrayContaining(['build', 'consumer']));
    expect(workflow.jobs.stage.if).toBe("github.event_name == 'push'");
    expect(workflow.jobs.stage.environment).toBe('npm-tum-ui');
});

describe('TUM UI release validation', () => {
    let directory;
    const gitEnvironment = { ...process.env, GIT_CONFIG_GLOBAL: '/dev/null', GIT_CONFIG_NOSYSTEM: '1' };
    const git = (...args) => execFileSync('git', args, { cwd: directory, stdio: 'pipe', env: gitEnvironment });

    beforeEach(() => {
        directory = mkdtempSync(resolve(tmpdir(), 'tum-ui-release-'));
        mkdirSync(resolve(directory, 'packages/tum-ui'), { recursive: true });
        mkdirSync(resolve(directory, 'node_modules'));
        symlinkSync(resolve(root, 'node_modules/semver'), resolve(directory, 'node_modules/semver'));
        git('init', '--quiet');
        git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '--quiet', '--allow-empty', '-m', 'Reviewed commit');
        git('update-ref', 'refs/remotes/origin/develop', 'HEAD');
    });

    afterEach(() => rmSync(directory, { recursive: true, force: true }));

    it.each([
        { name: 'accepts a reviewed release', accepted: true },
        { name: 'allows rehearsal on an unmerged commit', event: 'workflow_dispatch', unmerged: true, accepted: true },
        { name: 'rejects a tag for a different version', tag: 'tum-ui-v2.0.0' },
        { name: 'rejects a noncanonical version', version: 'v1.2.3' },
        { name: 'rejects a prerelease', version: '1.2.3-rc.0' },
        { name: 'rejects build metadata', version: '1.2.3+build' },
        { name: 'rejects an unmerged release commit', unmerged: true },
    ])('$name', ({ version = '1.2.3', tag = `tum-ui-v${version}`, event = 'push', unmerged = false, accepted = false }) => {
        writeFileSync(resolve(directory, 'packages/tum-ui/package.json'), JSON.stringify({ version }));
        if (unmerged) {
            git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '--quiet', '--allow-empty', '-m', 'Unreviewed commit');
        }
        const result = spawnSync('bash', ['-eo', 'pipefail', '-c', validator], {
            cwd: directory,
            env: { ...gitEnvironment, RELEASE_EVENT: event, RELEASE_REF: tag },
            encoding: 'utf8',
        });
        expect(result.status, result.stderr).toBe(accepted ? 0 : 1);
    });
});
