import fs from 'fs';
import os from 'os';
import path from 'path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { getServerFileCoverage } from './local-pr-coverage.mjs';

const PACKAGE = 'de/tum/cit/aet/artemis/metis/repository';
const FILE = `${PACKAGE}/MetisCheckRepository.java`;
const NO_LINES = 'N/A (no executable lines)';

function sourceFile(name, missed, covered) {
    return `<sourcefile name="${name}"><line nr="1" mi="0" ci="1" mb="0" cb="0"/><counter type="LINE" missed="${missed}" covered="${covered}"/></sourcefile>`;
}

function packageBlock(name, content) {
    return `<package name="${name}">${content}</package>`;
}

describe('getServerFileCoverage', () => {
    let coverageDir;

    beforeEach(() => {
        coverageDir = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-pr-coverage-'));
    });

    afterEach(() => {
        fs.rmSync(coverageDir, { recursive: true, force: true });
    });

    function report(moduleName, content) {
        const directory = path.join(coverageDir, moduleName);
        fs.mkdirSync(directory, { recursive: true });
        fs.writeFileSync(path.join(directory, 'jacocoTestReport.xml'), `<?xml version="1.0"?><report name="Artemis">${content}</report>`);
    }

    it('does not borrow a following package counter for a self-closing interface', () => {
        // The shape that made MetisCheckRepository borrow TumLiveApi's 60% in the PR report.
        report(
            'aggregated',
            packageBlock(PACKAGE, '<sourcefile name="MetisCheckRepository.java"/>') + packageBlock('de/tum/cit/aet/artemis/lecture/service', sourceFile('TumLiveApi.java', 2, 3)),
        );
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe(NO_LINES);
        expect(getServerFileCoverage('de/tum/cit/aet/artemis/lecture/service/TumLiveApi.java', 'lecture', coverageDir)).toBe('60.00%');
    });

    it.each(['<sourcefile name="MetisCheckRepository.java"/>', '<sourcefile name="MetisCheckRepository.java"></sourcefile>'])(
        'does not borrow a sibling counter after %s',
        (emptySource) => {
            report('metis', packageBlock(PACKAGE, emptySource + sourceFile('Other.java', 2, 3)));
            expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe(NO_LINES);
        },
    );

    it('uses the full package and case-sensitive source name', () => {
        report(
            'aggregated',
            packageBlock('de/tum/cit/aet/artemis/core/repository', sourceFile('MetisCheckRepository.java', 9, 1)) +
                packageBlock(PACKAGE, sourceFile('metischeckrepository.java', 5, 5) + sourceFile('MetisCheckRepository.java', 1, 9)),
        );
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe('90.00%');
    });

    it('uses source-file totals, including default methods and inner classes, rather than class or package counters', () => {
        report(
            'metis',
            packageBlock(
                PACKAGE,
                '<class name="Example"><counter type="LINE" missed="0" covered="1"/></class>' +
                    sourceFile('MetisCheckRepository.java', 1, 2) +
                    '<counter type="LINE" missed="10" covered="90"/>',
            ),
        );
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe('66.67%');
    });

    it.each([
        [10, 0, '0.00%'],
        [0, 10, '100.00%'],
        [0, 0, NO_LINES],
    ])('reports %i missed and %i covered lines as %s', (missed, covered, expected) => {
        report('metis', packageBlock(PACKAGE, sourceFile('MetisCheckRepository.java', missed, covered)));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe(expected);
    });

    it('allows reordered counter attributes and whitespace', () => {
        report('metis', packageBlock(PACKAGE, '<sourcefile name="MetisCheckRepository.java">\n<counter covered="3" type="LINE" missed="1" />\n</sourcefile>'));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe('75.00%');
    });

    it('prefers a module report, including a source with no executable lines', () => {
        report('aggregated', packageBlock(PACKAGE, sourceFile('MetisCheckRepository.java', 2, 3)));
        report('metis', packageBlock(PACKAGE, sourceFile('MetisCheckRepository.java', 1, 9)));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe('90.00%');
        report('metis', packageBlock(PACKAGE, '<sourcefile name="MetisCheckRepository.java"/>'));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe(NO_LINES);
    });

    it.each([
        packageBlock(PACKAGE, sourceFile('Other.java', 0, 10)),
        packageBlock('another/package', sourceFile('MetisCheckRepository.java', 0, 10)),
        packageBlock(PACKAGE, '<sourcefile name="MetisCheckRepository.java"><counter type="LINE" covered="3"/></sourcefile>'),
    ])('falls back to the aggregate when the module report has no usable matching entry', (moduleContent) => {
        report('metis', moduleContent);
        report('aggregated', packageBlock(PACKAGE, sourceFile('MetisCheckRepository.java', 2, 3)));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBe('60.00%');
    });

    it('distinguishes missing files and reports from files with no executable lines', () => {
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBeNull();
        report('aggregated', packageBlock(PACKAGE, sourceFile('Other.java', 2, 3)));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBeNull();
        report('aggregated', packageBlock('another/package', sourceFile('MetisCheckRepository.java', 2, 3)));
        expect(getServerFileCoverage(FILE, 'metis', coverageDir)).toBeNull();
    });

    it('escapes regex characters in source names', () => {
        report('metis', packageBlock(PACKAGE, sourceFile('Example$RepositoryXjava', 0, 10) + sourceFile('Example$Repository.java', 1, 1)));
        expect(getServerFileCoverage(`${PACKAGE}/Example$Repository.java`, 'metis', coverageDir)).toBe('50.00%');
    });
});
