import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import process from 'node:process';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { parse } from 'yaml';

const root = resolve(import.meta.dirname, '..');
const workflow = parse(readFileSync(resolve(root, '.github/workflows/release-tum-aet-ui.yml'), 'utf8'));
const validator = workflow.jobs.build.steps.find((step) => step.name === 'Validate release identity').run;

it('withholds publishing credentials until build and consumer checks pass', () => {
    expect(workflow.permissions).toEqual({});
    expect(
        Object.entries(workflow.jobs)
            .filter(([, job]) => job.permissions?.['id-token'] === 'write')
            .map(([name]) => name),
    ).toEqual(['stage']);
    expect(workflow.jobs.stage.needs).toEqual(expect.arrayContaining(['build', 'consumer']));
    expect(workflow.on).toEqual({ workflow_dispatch: null });
    expect(workflow.jobs.stage.if).toBe("github.ref_type == 'tag'");
    expect(workflow.jobs.stage.environment).toBe('npm-tum-aet-ui');
});

describe('TUM AET UI release validation', () => {
    let directory;
    const gitEnvironment = { ...process.env, GIT_CONFIG_GLOBAL: '/dev/null', GIT_CONFIG_NOSYSTEM: '1' };
    const git = (...args) => execFileSync('git', args, { cwd: directory, stdio: 'pipe', env: gitEnvironment });

    beforeEach(() => {
        directory = mkdtempSync(resolve(tmpdir(), 'tum-aet-ui-release-'));
        mkdirSync(resolve(directory, 'packages/tum-aet-ui'), { recursive: true });
        mkdirSync(resolve(directory, 'node_modules'));
        symlinkSync(resolve(root, 'node_modules/semver'), resolve(directory, 'node_modules/semver'));
        git('init', '--quiet');
        git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '--quiet', '--allow-empty', '-m', 'Reviewed commit');
        git('update-ref', 'refs/remotes/origin/develop', 'HEAD');
    });

    afterEach(() => rmSync(directory, { recursive: true, force: true }));

    it.each([
        { name: 'accepts a reviewed release', accepted: true },
        { name: 'allows rehearsal on an unmerged commit', refType: 'branch', ref: 'feature/ui', unmerged: true, accepted: true },
        { name: 'rejects a tag for a different version', ref: '@tumaet/ui-angular@2.0.0' },
        { name: 'rejects another package tag', ref: '@tumaet/apollon@1.2.3' },
        { name: 'rejects an Artemis application tag', ref: '1.2.3' },
        { name: 'rejects a noncanonical version', version: 'v1.2.3' },
        { name: 'rejects a prerelease', version: '1.2.3-rc.0' },
        { name: 'rejects build metadata', version: '1.2.3+build' },
        { name: 'rejects an unmerged release commit', unmerged: true },
    ])('$name', ({ version = '1.2.3', ref = `@tumaet/ui-angular@${version}`, refType = 'tag', unmerged = false, accepted = false }) => {
        writeFileSync(resolve(directory, 'packages/tum-aet-ui/package.json'), JSON.stringify({ name: '@tumaet/ui-angular', version }));
        if (unmerged) {
            git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '--quiet', '--allow-empty', '-m', 'Unreviewed commit');
        }
        const result = spawnSync('bash', ['-eo', 'pipefail', '-c', validator], {
            cwd: directory,
            env: { ...gitEnvironment, RELEASE_REF_TYPE: refType, RELEASE_REF: ref },
            encoding: 'utf8',
        });
        expect(result.status, result.stderr).toBe(accepted ? 0 : 1);
    });
});
