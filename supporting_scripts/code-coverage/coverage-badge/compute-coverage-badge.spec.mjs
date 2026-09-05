import { spawnSync } from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';
import { fileURLToPath } from 'url';
import { describe, expect, it } from 'vitest';
import { colorFor, computeBadge, parseJacocoLines, parseVitestLines } from './compute-coverage-badge.mjs';

/**
 * Builds a JaCoCo report whose shape matches the real aggregated report: per-package counters that
 * must be ignored, followed by the report-level counters that must be read.
 */
function jacocoReport({ missed = 11652, covered = 74256, packages = 1 } = {}) {
    const packageBlocks = Array.from(
        { length: packages },
        (unused, index) =>
            `<package name="de/tum/cit/aet/artemis/pkg${index}">` +
            `<counter type="INSTRUCTION" missed="7" covered="318"/>` +
            `<counter type="LINE" missed="999" covered="999"/>` +
            `</package>`,
    ).join('');
    return (
        `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><report name="Artemis">` +
        packageBlocks +
        `<counter type="INSTRUCTION" missed="60587" covered="383401"/>` +
        `<counter type="LINE" missed="${missed}" covered="${covered}"/>` +
        `<counter type="CLASS" missed="151" covered="3129"/>` +
        `</report>`
    );
}

function vitestSummary({ total = 78212, covered = 70728 } = {}) {
    return { total: { lines: { total, covered, skipped: 0, pct: 90.43 } } };
}

/** A published badge matching the fixtures above, used as the `previous` value. */
function publishedBadge({ message = '88.3%', color = 'green', total = 164120 } = {}) {
    return { schemaVersion: 1, label: 'coverage', message, color, combined: { covered: 144984, total } };
}

describe('parseJacocoLines', () => {
    it('reads the report-level LINE counter and ignores the per-package ones', () => {
        // Two packages each carry a decoy LINE counter of 999/999; summing them all would inflate the result.
        expect(parseJacocoLines(jacocoReport({ packages: 2 }))).toEqual({ covered: 74256, total: 85908 });
    });

    it('tolerates the attribute order being swapped', () => {
        const xml = '<report><package name="p"></package><counter covered="30" type="LINE" missed="70"/></report>';
        expect(parseJacocoLines(xml)).toEqual({ covered: 30, total: 100 });
    });

    it('returns undefined for a report with no packages, which carries no totals', () => {
        expect(parseJacocoLines('<report name="Artemis"></report>')).toBeUndefined();
    });

    it('returns undefined when the report has packages but no report-level LINE counter', () => {
        expect(parseJacocoLines('<report><package name="p"></package><counter type="CLASS" missed="1" covered="2"/></report>')).toBeUndefined();
    });

    it('returns undefined for truncated or absent input', () => {
        expect(parseJacocoLines(undefined)).toBeUndefined();
        expect(parseJacocoLines('<?xml version="1.0"?><report name="Artemis"><package name="p">')).toBeUndefined();
    });

    it('rejects a report truncated mid-package instead of reading a sourcefile-level counter', () => {
        // The dangerous case: the upload was cut short partway through a later package. Anchoring on
        // the last `</package>` then lands on an EARLIER package's close, and the next LINE counter
        // belongs to a <sourcefile> — small, plausible numbers that would publish a wrong badge.
        const truncated =
            `<report name="Artemis">` +
            `<package name="a"><counter type="LINE" missed="10" covered="90"/></package>` +
            `<package name="b"><sourcefile name="B.java"><counter type="LINE" missed="7" covered="13"/></sourcefile>`;
        expect(parseJacocoLines(truncated)).toBeUndefined();
    });

    it('rejects a <group>-structured aggregate rather than reporting one group as the total', () => {
        // Ant/Maven aggregates nest packages inside <group>. Gradle does not emit this shape, but if
        // it ever did, the naive anchor would read the last group's counter as the report total.
        const grouped =
            `<report name="Artemis">` +
            `<group name="one"><package name="a"></package><counter type="LINE" missed="1000" covered="9000"/></group>` +
            `<group name="two"><package name="b"></package><counter type="LINE" missed="5" covered="5"/></group>` +
            `<counter type="LINE" missed="1005" covered="9005"/></report>`;
        expect(parseJacocoLines(grouped)).toBeUndefined();
    });

    it('accepts the real report shape, where only counters separate the last package from </report>', () => {
        const withWhitespace = '<report><package name="p"></package>\n  <counter type="LINE" missed="1" covered="9"/>\n</report>\n';
        expect(parseJacocoLines(withWhitespace)).toEqual({ covered: 9, total: 10 });
    });
});

describe('parseVitestLines', () => {
    it('reads total.lines', () => {
        expect(parseVitestLines(vitestSummary())).toEqual({ covered: 70728, total: 78212 });
    });

    it('returns undefined when the totals are absent or non-numeric', () => {
        expect(parseVitestLines(undefined)).toBeUndefined();
        expect(parseVitestLines({})).toBeUndefined();
        expect(parseVitestLines({ total: {} })).toBeUndefined();
        expect(parseVitestLines({ total: { lines: { total: 'n/a', covered: 1 } } })).toBeUndefined();
    });

    it('rejects counts that are impossible, rather than publishing a nonsense percentage', () => {
        // A badge is published unattended, so covered > total must not become ">100%".
        expect(parseVitestLines(vitestSummary({ covered: 1000000, total: 1 }))).toBeUndefined();
        expect(parseVitestLines(vitestSummary({ covered: -1, total: 100 }))).toBeUndefined();
        expect(parseVitestLines(vitestSummary({ covered: 1, total: -100 }))).toBeUndefined();
        expect(parseVitestLines(vitestSummary({ covered: 1.5, total: 100 }))).toBeUndefined();
        expect(parseVitestLines(vitestSummary({ covered: 1, total: Number.MAX_SAFE_INTEGER + 2 }))).toBeUndefined();
        // The boundary case is legitimate: a fully covered client.
        expect(parseVitestLines(vitestSummary({ covered: 100, total: 100 }))).toEqual({ covered: 100, total: 100 });
    });
});

describe('colorFor', () => {
    it('maps percentages onto Shields colours at the band boundaries', () => {
        expect(colorFor(90)).toBe('brightgreen');
        expect(colorFor(89.9)).toBe('green');
        expect(colorFor(80)).toBe('green');
        expect(colorFor(70)).toBe('yellowgreen');
        expect(colorFor(60)).toBe('yellow');
        expect(colorFor(50)).toBe('orange');
        expect(colorFor(49.9)).toBe('red');
        expect(colorFor(0)).toBe('red');
    });
});

describe('computeBadge', () => {
    const validInputs = { jacocoXml: jacocoReport(), vitestSummary: vitestSummary() };

    it('combines server and client lines weighted by size', () => {
        const result = computeBadge({ ...validInputs, sha: 'e62c69b', now: '2026-08-29T10:00:00Z' });

        // (74256 + 70728) / (85908 + 78212) = 144984 / 164120 = 88.34%
        expect(result).toEqual({
            status: 'publish',
            badge: {
                schemaVersion: 1,
                label: 'coverage',
                message: '88.3%',
                color: 'green',
                exact: 88.34,
                server: { covered: 74256, total: 85908 },
                client: { covered: 70728, total: 78212 },
                combined: { covered: 144984, total: 164120 },
                sha: 'e62c69b',
                updatedAt: '2026-08-29T10:00:00Z',
            },
        });
    });

    it('weights by codebase size rather than averaging the two percentages', () => {
        // A tiny 0%-covered client next to a large 90%-covered server must barely move the number;
        // a naive mean of the two percentages would report 45%.
        const result = computeBadge({
            jacocoXml: jacocoReport({ covered: 90000, missed: 10000 }),
            vitestSummary: vitestSummary({ covered: 1, total: 100 }),
        });
        expect(result.status).toBe('publish');
        expect(result.badge.message).toBe('89.9%');
    });

    it('publishes on the very first run, when nothing has been published yet', () => {
        expect(computeBadge({ ...validInputs, previous: undefined }).status).toBe('publish');
    });

    it('keeps the previous badge when the server report is missing', () => {
        const result = computeBadge({ vitestSummary: vitestSummary() });
        expect(result.status).toBe('skip');
        expect(result.reason).toContain('server JaCoCo report is missing');
    });

    it('keeps the previous badge when the client summary is missing', () => {
        const result = computeBadge({ jacocoXml: jacocoReport() });
        expect(result.status).toBe('skip');
        expect(result.reason).toContain('client Vitest summary is missing');
    });

    it('keeps the previous badge when a report measured zero lines', () => {
        const result = computeBadge({ jacocoXml: jacocoReport({ covered: 0, missed: 0 }), vitestSummary: vitestSummary() });
        expect(result.status).toBe('skip');
        // 'measured', not just 'zero lines' — the covered-zero case below carries a similar message.
        expect(result.reason).toContain('measured zero lines');
    });

    it('keeps the previous badge when a report covered zero lines', () => {
        const result = computeBadge({ jacocoXml: jacocoReport({ covered: 0, missed: 85908 }), vitestSummary: vitestSummary() });
        expect(result.status).toBe('skip');
        expect(result.reason).toContain('covered zero lines');
    });

    it('keeps the previous badge when the line total collapses, which means a partial report', () => {
        // Only the client half survived: 78212 is well under half of the published 164120.
        const result = computeBadge({
            jacocoXml: jacocoReport({ covered: 100, missed: 20 }),
            vitestSummary: vitestSummary(),
            previous: publishedBadge(),
        });
        expect(result.status).toBe('skip');
        expect(result.reason).toContain('collapsed');
    });

    it('still publishes a genuine coverage regression, which leaves the total intact', () => {
        // Half the server lines stop being covered, but the denominator is unchanged, so the guard
        // must not fire — otherwise the badge would freeze at its best-ever value.
        const result = computeBadge({
            jacocoXml: jacocoReport({ covered: 37128, missed: 48780 }),
            vitestSummary: vitestSummary(),
            previous: publishedBadge(),
        });
        expect(result.status).toBe('publish');
        expect(result.badge.message).toBe('65.7%');
    });

    it('does not republish an unchanged value, so the badges branch stays quiet', () => {
        const result = computeBadge({ ...validInputs, previous: publishedBadge({ message: '88.3%' }) });
        expect(result.status).toBe('skip');
        expect(result.reason).toContain('unchanged');
    });

    it('republishes when the rounded message is unchanged but the colour threshold moved', () => {
        const result = computeBadge({
            jacocoXml: jacocoReport({ covered: 4498, missed: 502 }),
            vitestSummary: vitestSummary({ covered: 4498, total: 5000 }),
            previous: publishedBadge({ message: '90.0%', color: 'brightgreen', total: 10000 }),
        });

        // 8996 / 10000 = 89.96%, which rounds to 90.0% but belongs to the green band.
        expect(result.status).toBe('publish');
        expect(result.badge).toMatchObject({ message: '90.0%', color: 'green' });
    });

    it('separates the healthy no-op from a tripped guard, which CI reports differently', () => {
        // An unchanged value is routine; a tripped guard is not, because the collapse guard latches
        // on the last published total and would otherwise freeze the badge with no signal.
        expect(computeBadge({ ...validInputs, previous: publishedBadge() }).kind).toBe('unchanged');
        expect(computeBadge({ vitestSummary: vitestSummary() }).kind).toBe('guard');
        expect(
            computeBadge({
                jacocoXml: jacocoReport({ covered: 100, missed: 20 }),
                vitestSummary: vitestSummary(),
                previous: publishedBadge(),
            }).kind,
        ).toBe('guard');
    });

    it('republishes once the first decimal moves', () => {
        const result = computeBadge({ ...validInputs, previous: publishedBadge({ message: '88.2%' }) });
        expect(result.status).toBe('publish');
        expect(result.badge.message).toBe('88.3%');
    });

    it('ignores a previous value that carries no combined total', () => {
        const result = computeBadge({ ...validInputs, previous: { message: '1.0%' } });
        expect(result.status).toBe('publish');
    });
});

describe('the CLI', () => {
    const script = fileURLToPath(new URL('./compute-coverage-badge.mjs', import.meta.url));

    /** Runs the script in a throwaway directory. Arguments starting with `@` resolve into that directory. */
    function run(args, files = {}) {
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'coverage-badge-'));
        for (const [name, contents] of Object.entries(files)) {
            fs.writeFileSync(path.join(dir, name), contents);
        }
        const resolved = args.map((arg) => (arg.startsWith('@') ? path.join(dir, arg.slice(1)) : arg));
        const result = spawnSync(process.execPath, [script, ...resolved], { encoding: 'utf-8' });
        return { dir, code: result.status, stdout: result.stdout };
    }

    const reports = { 'report.xml': jacocoReport(), 'summary.json': JSON.stringify(vitestSummary()) };

    it('exits 0 and writes the badge when nothing has been published yet', () => {
        const { dir, code, stdout } = run(['--jacoco', '@report.xml', '--vitest', '@summary.json', '--out', '@coverage.json'], reports);

        expect(code).toBe(0);
        expect(stdout).toContain('88.3%');
        expect(JSON.parse(fs.readFileSync(path.join(dir, 'coverage.json'), 'utf-8'))).toMatchObject({ message: '88.3%', color: 'green' });
    });

    it('reads --previous before writing when both name the same file, as CI invokes it', () => {
        // CI aliases --previous and --out. Were the write to happen first, the "previous" read would
        // see the value just written and the unchanged-check would be meaningless.
        const previous = JSON.stringify({ ...publishedBadge({ message: '10.0%' }), color: 'red' });
        const { dir, code, stdout } = run(['--jacoco', '@report.xml', '--vitest', '@summary.json', '--previous', '@coverage.json', '--out', '@coverage.json'], {
            ...reports,
            'coverage.json': previous,
        });

        expect(code).toBe(0);
        expect(stdout).toContain('88.3%');
        expect(JSON.parse(fs.readFileSync(path.join(dir, 'coverage.json'), 'utf-8')).message).toBe('88.3%');
    });

    it('exits 3 and leaves the file untouched when the value is unchanged', () => {
        const previous = JSON.stringify(publishedBadge({ message: '88.3%' }));
        const { dir, code, stdout } = run(['--jacoco', '@report.xml', '--vitest', '@summary.json', '--previous', '@coverage.json', '--out', '@coverage.json'], {
            ...reports,
            'coverage.json': previous,
        });

        expect(code).toBe(3);
        expect(stdout).toContain('unchanged');
        expect(fs.readFileSync(path.join(dir, 'coverage.json'), 'utf-8')).toBe(previous);
    });

    it('exits 4 when a guard rejects the value, which CI surfaces as a warning', () => {
        const { code, stdout } = run(['--jacoco', '@missing.xml', '--vitest', '@summary.json', '--out', '@coverage.json'], reports);

        expect(code).toBe(4);
        expect(stdout).toContain('missing');
    });

    it('exits 1 on a bad invocation', () => {
        expect(run(['--jacoco', '@report.xml'], reports).code).toBe(1);
    });
});
