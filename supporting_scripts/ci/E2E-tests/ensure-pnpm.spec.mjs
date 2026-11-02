import { describe, expect, it } from 'vitest';
import { chmodSync, copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

// The Playwright image of the E2E stack lost corepack once, which stopped every E2E run before a test started. These cases run the script
// in a temporary copy of the repository layout and with a PATH that holds only fake tools, so that they depend neither on what is
// installed on the machine that runs them nor on the real package.json.
const scriptSource = path.resolve('supporting_scripts/ci/E2E-tests/ensure-pnpm.sh');

function run({ withCorepack, packageJson }) {
    const root = mkdtempSync(path.join(tmpdir(), 'artemis-ensure-pnpm-'));
    const bin = path.join(root, 'bin');
    const calls = path.join(root, 'calls.log');
    try {
        mkdirSync(bin);
        const scriptDirectory = path.join(root, 'supporting_scripts/ci/E2E-tests');
        mkdirSync(scriptDirectory, { recursive: true });
        copyFileSync(scriptSource, path.join(scriptDirectory, 'ensure-pnpm.sh'));
        writeFileSync(path.join(root, 'package.json'), JSON.stringify(packageJson));

        const shim = (name) => {
            writeFileSync(path.join(bin, name), `#!/bin/sh\necho "${name} $*" >> "${calls}"\n`);
            chmodSync(path.join(bin, name), 0o700);
        };
        shim('npm');
        if (withCorepack) {
            shim('corepack');
        }
        // the real node reads the package.json and dirname finds the root, everything else is fake
        symlinkSync(process.execPath, path.join(bin, 'node'));
        symlinkSync(spawnSync('sh', ['-c', 'command -v dirname'], { encoding: 'utf8' }).stdout.trim(), path.join(bin, 'dirname'));

        const result = spawnSync('/bin/sh', [path.join(scriptDirectory, 'ensure-pnpm.sh')], { encoding: 'utf8', env: { PATH: bin } });
        let log = '';
        try {
            log = readFileSync(calls, 'utf8');
        } catch {
            // no tool was called
        }
        return { status: result.status, stderr: result.stderr, calls: log.trim().split('\n').filter(Boolean) };
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
}

describe('ensure-pnpm.sh', () => {
    it('enables corepack when the image has it and installs nothing', () => {
        const result = run({ withCorepack: true, packageJson: { packageManager: 'pnpm@12.15.0' } });

        expect(result.status).toBe(0);
        expect(result.calls).toEqual(['corepack enable']);
    });

    it('installs the pnpm that package.json pins with npm when the image has no corepack', () => {
        const result = run({ withCorepack: false, packageJson: { packageManager: 'pnpm@12.15.0' } });

        expect(result.status).toBe(0);
        expect(result.calls).toEqual(['npm install --global pnpm@12.15.0']);
    });

    it('follows the version of package.json instead of a version of its own', () => {
        const result = run({ withCorepack: false, packageJson: { packageManager: 'pnpm@13.0.1' } });

        expect(result.calls).toEqual(['npm install --global pnpm@13.0.1']);
    });

    it.each([{ packageManager: 'yarn@4.0.0' }, {}])('fails without installing anything when package.json does not pin pnpm: %j', (packageJson) => {
        const result = run({ withCorepack: false, packageJson });

        expect(result.status).toBe(1);
        expect(result.stderr).toContain('does not pin pnpm in packageManager');
        expect(result.calls).toEqual([]);
    });

    it.each(['deployment/docker/playwright.yml', 'supporting_scripts/ci/E2E-tests/execute-locally.sh', 'supporting_scripts/e2e/run-e2e-tests-local-multinode.sh'])(
        'is what the Playwright container of %s runs instead of corepack itself',
        (file) => {
            const source = readFileSync(file, 'utf8');

            expect(source).toContain('sh supporting_scripts/ci/E2E-tests/ensure-pnpm.sh');
            expect(source).not.toMatch(/^\s*corepack enable/m);
        },
    );

    it('is satisfied by the real package.json of the repository', () => {
        const pinned = JSON.parse(readFileSync('package.json', 'utf8')).packageManager;

        expect(pinned).toMatch(/^pnpm@\d+\.\d+\.\d+/);
    });
});
