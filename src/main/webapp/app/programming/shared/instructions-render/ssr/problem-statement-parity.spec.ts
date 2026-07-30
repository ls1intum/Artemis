import { TestBed } from '@angular/core/testing';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { of } from 'rxjs';
import { PluginSimple } from 'markdown-it';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { ProgrammingExerciseTaskExtensionWrapper, taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExercisePlantUmlService } from 'app/programming/shared/instructions-render/services/programming-exercise-plant-uml.service';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

const CORPUS = 'src/test/resources/test-data/problem-statements';

/** Translates the legacy states into the server's status vocabulary so both sides compare as strings. */
const LEGACY_TO_SERVER: Record<TestCaseState, string> = {
    [TestCaseState.SUCCESS]: 'success',
    [TestCaseState.FAIL]: 'fail',
    [TestCaseState.NOT_EXECUTED]: 'not-executed',
    [TestCaseState.NO_RESULT]: 'no-result',
};

function serverStatuses(html: string): string[] {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    return [...doc.querySelectorAll('.artemis-task')].map((element) => element.getAttribute('data-test-status') ?? '');
}

/** The authored reference list of each task, in document order. */
function authoredRefLists(markdown: string): string[] {
    return [...markdown.matchAll(taskRegex)].map((match) => match[2] ?? '');
}

/**
 * Splits an authored reference list on top-level commas only, mirroring TestReferenceParser on the server.
 * A plain comma split is wrong for parameterized names such as `testInsert(InsertMock, 1)`.
 */
function splitTestReferences(refList: string): string[] {
    const references: string[] = [];
    let depth = 0;
    let current = '';
    for (const character of refList) {
        if (character === ',' && depth === 0) {
            if (current.trim()) {
                references.push(current.trim());
            }
            current = '';
            continue;
        }
        if (character === '(') {
            depth++;
        } else if (character === ')') {
            depth--;
        }
        current += character;
    }
    if (current.trim()) {
        references.push(current.trim());
    }
    return references;
}

/** Structural fingerprint of a rendering, ignoring the parts the two pipelines legitimately differ on. */
function structure(html: string): { headings: string[]; codeBlocks: number; highlightedCodeBlocks: number; tables: number; alerts: number; links: number } {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    return {
        headings: [...doc.querySelectorAll('h1, h2, h3, h4')].map((element) => (element.textContent ?? '').trim()),
        codeBlocks: doc.querySelectorAll('pre').length,
        // .hljs is what the legacy pipeline's highlight.js integration adds (markdown.conversion.util.ts:50).
        // A `language-x` class alone proves nothing: CommonMark emits it without performing any highlighting.
        highlightedCodeBlocks: doc.querySelectorAll('pre code.hljs, pre .hljs').length,
        tables: doc.querySelectorAll('table').length,
        alerts: doc.querySelectorAll('.markdown-alert').length,
        links: doc.querySelectorAll('a[href]').length,
    };
}

describe('problem statement rendering parity', () => {
    let instructionService: ProgrammingExerciseInstructionService;
    let extensions: PluginSimple[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ProgrammingExerciseInstructionService,
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                // Stubbed: the wrapper transitively needs HttpClient and ThemeService, and diagrams are excluded
                // from this comparison anyway.
                { provide: ProgrammingExercisePlantUmlService, useValue: { getPlantUmlSvg: () => of('<svg></svg>'), getPlantUmlImage: () => of('') } },
            ],
        });
        instructionService = TestBed.inject(ProgrammingExerciseInstructionService);
        extensions = [TestBed.inject(ProgrammingExerciseTaskExtensionWrapper).getExtension(), TestBed.inject(ProgrammingExercisePlantUmlExtensionWrapper).getExtension()];
    });

    // README.md documents the corpus but is not corpus content itself (it contains a literal `[task][name](refs)`
    // syntax example in prose, which would otherwise be picked up as a bogus "corpus file").
    const files = readdirSync(CORPUS).filter((name) => name.endsWith('.md') && name !== 'README.md');

    const readPair = (name: string) => ({
        markdown: readFileSync(join(CORPUS, name), 'utf8'),
        serverHtml: readFileSync(join(CORPUS, 'rendered', name.replace('.md', '.html')), 'utf8'),
    });

    it.each(files)('server and legacy status engines agree for %s', (name) => {
        const markdown = readFileSync(join(CORPUS, name), 'utf8');
        const serverHtml = readFileSync(join(CORPUS, 'rendered', name.replace('.md', '.html')), 'utf8');

        // Rebuild exactly the data the fixture was generated with: one test case per distinct authored reference,
        // numbered in first-appearance order, all passing.
        const distinctRefs: string[] = [];
        for (const refList of authoredRefLists(markdown)) {
            for (const ref of splitTestReferences(refList)) {
                if (!distinctRefs.includes(ref)) {
                    distinctRefs.push(ref);
                }
            }
        }
        const testCases = distinctRefs.map((ref, index) => ({ id: index + 1, testName: ref }) as ProgrammingExerciseTestCase);
        const result = { id: 1, successful: true, feedbacks: testCases.map((testCase) => ({ testCase, positive: true })) } as Result;

        const legacy = authoredRefLists(markdown).map((refList) => {
            const testIds = instructionService.convertTestListToIds(refList, testCases);
            return LEGACY_TO_SERVER[instructionService.testStatusForTask(testIds, result).testCaseState];
        });

        expect(serverStatuses(serverHtml)).toEqual(legacy);
    });

    // Second comparison: the markdown output itself. Status parity alone does not satisfy the spec's gate, which
    // exists to expose the deferred markdown gaps. Extensions are assembled exactly as the component does
    // (programming-exercise-instruction.component.ts:271).
    it.each(files)('server and legacy markdown output agree on supported features for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);
        const legacy = structure(htmlForMarkdown(markdown, extensions));
        const server = structure(serverHtml);

        expect(server.headings).toEqual(legacy.headings);
        expect(server.tables).toBe(legacy.tables);
        expect(server.codeBlocks).toBe(legacy.codeBlocks);
    });

    // The measured gaps live in their own test because only markdown-features.md exercises these features;
    // asserting them across the whole corpus would fail on fixtures that contain no code, alert or URL.
    it('measures the known markdown gaps against markdown-features.md', () => {
        const { markdown, serverHtml } = readPair('markdown-features.md');
        const legacy = structure(htmlForMarkdown(markdown, extensions));
        const server = structure(serverHtml);

        // Both sides are pinned to their observed counts, so closing a gap server-side deliberately fails this test
        // and forces the numbers (and the recorded parity findings) to be updated.
        expect(legacy.highlightedCodeBlocks).toBe(1);
        expect(server.highlightedCodeBlocks).toBe(0);
        expect(legacy.alerts).toBe(1);
        expect(server.alerts).toBe(0);
        expect(legacy.links).toBe(1);
        expect(server.links).toBe(0);
    });
});
