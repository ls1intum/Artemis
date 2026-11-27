import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

const script = resolve(dirname(fileURLToPath(import.meta.url)), 'sbom-filter-by-bundle.mjs');

const component = (group, name, version) => ({ 'bom-ref': `pkg:npm/${group}/${name}@${version}`, group, name, version });
const ANGULAR_SOURCE = '../node_modules/.pnpm/@angular+core@22.0.0/node_modules/@angular/core/fesm2022/core.mjs';
const GENERATED_ICON_SOURCE = '../build/fontawesome/free-regular-svg-icons/faCircle.ts';

describe('sbom-filter-by-bundle', () => {
    let directory;

    beforeEach(() => {
        directory = mkdtempSync(join(tmpdir(), 'sbom-filter-'));
        mkdirSync(join(directory, 'browser'));
    });

    afterEach(() => {
        rmSync(directory, { recursive: true, force: true });
    });

    /** Runs the filter on a bundle whose one source map lists the sources, and returns the process result and the names of the components that it kept. */
    function filter(sources, components, provenance) {
        writeFileSync(join(directory, 'browser', 'main.js.map'), JSON.stringify({ version: 3, sources }));
        writeFileSync(join(directory, 'bom.json'), JSON.stringify({ components, dependencies: [] }));
        const provenanceFile = join(directory, 'packages.json');
        if (provenance) writeFileSync(provenanceFile, JSON.stringify(provenance));
        const result = spawnSync(process.execPath, [script, join(directory, 'bom.json'), join(directory, 'filtered.json'), join(directory, 'browser')], {
            encoding: 'utf8',
            env: { ...process.env, FONTAWESOME_PROVENANCE_FILE: provenanceFile },
        });
        let kept;
        try {
            kept = JSON.parse(readFileSync(join(directory, 'filtered.json'), 'utf8')).components.map((c) => `${c.group}/${c.name}@${c.version}`);
        } catch {
            kept = undefined;
        }
        return { result, kept };
    }

    const bom = () => [
        component('@angular', 'core', '22.0.0'),
        component('@fortawesome', 'free-regular-svg-icons', '7.3.1'),
        component('@fortawesome', 'free-solid-svg-icons', '7.3.1'),
        component('', 'unused-package', '1.0.0'),
    ];

    it('keeps the package that the generated icon modules come from, which their path in the source map does not name', () => {
        const { result, kept } = filter([ANGULAR_SOURCE, GENERATED_ICON_SOURCE], bom(), { '@fortawesome/free-regular-svg-icons': '7.3.1' });

        expect(result.status, result.stderr).toBe(0);
        expect(kept).toEqual(['@angular/core@22.0.0', '@fortawesome/free-regular-svg-icons@7.3.1']);
    });

    it('does not need the provenance of the icons for a bundle without generated icon modules', () => {
        const { result, kept } = filter([ANGULAR_SOURCE], bom());

        expect(result.status, result.stderr).toBe(0);
        expect(kept).toEqual(['@angular/core@22.0.0']);
    });

    it('fails when the version that the icons were generated from is not in the SBOM, as it does for any package that drifted', () => {
        const { result } = filter([GENERATED_ICON_SOURCE], bom(), { '@fortawesome/free-regular-svg-icons': '7.2.0' });

        expect(result.status).toBe(1);
        expect(result.stderr).toContain('@fortawesome/free-regular-svg-icons@7.2.0');
    });

    it('fails instead of dropping the package when the file that names the packages of the generated icons is missing', () => {
        const { result } = filter([GENERATED_ICON_SOURCE], bom());

        expect(result.status).toBe(1);
        expect(result.stderr).toContain('run the prebuild');
    });

    it('fails when the file does not list the package of a generated icon directory', () => {
        const { result } = filter([GENERATED_ICON_SOURCE], bom(), { '@fortawesome/free-solid-svg-icons': '7.3.1' });

        expect(result.status).toBe(1);
        expect(result.stderr).toContain('@fortawesome/free-regular-svg-icons');
    });
});
