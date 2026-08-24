import { TestBed } from '@angular/core/testing';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { of } from 'rxjs';
import { MarkdownItPlugin } from 'app/foundation/util/markdown-it.types';
import { DialogService } from 'primeng/dynamicdialog';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { ProgrammingExerciseTaskExtensionWrapper, taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExercisePlantUmlService } from 'app/programming/shared/instructions-render/services/programming-exercise-plant-uml.service';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { highlightCodeBlocks, renderFormulas, sanitizeFragment } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame.util';
import { interactiveMessage, runFrameScript } from 'test/helpers/problem-statement-frame.helper';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const CORPUS = 'src/test/resources/test-data/problem-statements';

/**
 * The `server.url` the committed fixtures were rendered with (`src/test/resources/config/application.yml`). Only this
 * exact origin is ever stripped from a URL, so two *different* foreign origins can never compare as equal.
 */
const SERVER_ORIGIN = 'http://localhost';

// ---------------------------------------------------------------------------------------------------------------
// Layer 1: the canonicalized token stream.
//
// The two pipelines cannot be diffed as raw HTML, and not because of anything either of them gets wrong: the legacy
// side is only `htmlForMarkdown()` (no `prepareTasks()`, no Angular task injection), highlighting and KaTeX run at
// different stages on the two sides, PlantUML is asynchronous on the client and inline on the server, and the server
// absolutizes root-relative URLs and wraps its output in a container. Each of those is hidden by exactly one named
// canonicalizer below, each of which has its own focused test proving it hides its own difference and nothing else.
// Everything the canonicalizers do not name is compared exactly.
// ---------------------------------------------------------------------------------------------------------------

const SENTINEL_TAG = 'artemis-parity-sentinel';
const TOKEN_ATTRIBUTE = 'data-token';
const EXACT_TEXT_ATTRIBUTE = 'data-exact-text';

/**
 * The single allowlist of markup the gate compares. Everything outside it is a renderer-owned styling hook rather
 * than a semantic property: the legacy pipeline puts `class="table"` on every table (`MarkdownitTagClass`) and the
 * server does not, PlantUML container ids are generated differently by construction, and inline `style` only ever
 * appears in KaTeX output. Emitting any of those server-side purely so a diff lines up would be markup written for
 * the test rather than for the product, which is why the allowlist is the choice.
 *
 * `attributes` are compared verbatim; of an element's classes only those in `classes`, plus everything starting with
 * `classPrefix` (the `markdown-alert-<type>` family), take part. Sentinel payloads produced by the canonicalizers are
 * compared in full, since they *are* the comparison for the subtree they replaced.
 *
 * `artemis-task` is defensive and unreachable by construction: the task canonicalizer replaces every element carrying
 * it before tokenization, so no such element can reach the class comparison. It is listed so that a future change
 * narrowing that canonicalizer does not silently make the marker class uncompared. `hljs` is reachable only outside
 * `pre > code`, for the same reason.
 */
const COMPARED_MARKUP = {
    attributes: ['href', 'src', 'alt', 'title', 'colspan', 'rowspan', 'start', 'type'],
    classes: ['artemis-task', 'markdown-alert', 'markdown-alert-title', 'hljs'],
    classPrefix: 'markdown-alert-',
} as const;

/**
 * Tags whose surrounding whitespace is not rendered. The server pretty-prints its HTML and the legacy pipeline does
 * not, so the indentation between two block elements is a formatting artefact on one side only.
 */
const BLOCK_TAGS = new Set([
    'HTML',
    'BODY',
    'DIV',
    'P',
    'UL',
    'OL',
    'LI',
    'TABLE',
    'THEAD',
    'TBODY',
    'TFOOT',
    'TR',
    'TH',
    'TD',
    'H1',
    'H2',
    'H3',
    'H4',
    'H5',
    'H6',
    'PRE',
    'BLOCKQUOTE',
    'HR',
]);

const LANGUAGE_CLASS_PREFIX = 'language-';

/** Non-global clone of `taskRegex`: `exec` on the shared global instance would carry `lastIndex` between calls. */
const singleTaskRegex = new RegExp(taskRegex.source);

function createSentinel(document: Document, token: string, exactText?: string): HTMLElement {
    const sentinel = document.createElement(SENTINEL_TAG);
    sentinel.setAttribute(TOKEN_ATTRIBUTE, token);
    if (exactText !== undefined) {
        sentinel.setAttribute(EXACT_TEXT_ATTRIBUTE, exactText);
    }
    return sentinel;
}

/**
 * Canonicalizer "container": the server returns a whole HTML document whose statement sits in an
 * `.artemis-problem-statement` wrapper, while the legacy pipeline returns a bare fragment. Comparing the *children*
 * of the statement root hides the document shell and that one wrapper element, and nothing inside it.
 */
function problemStatementRoot(html: string): Element {
    const document = new DOMParser().parseFromString(html, 'text/html');
    return document.querySelector('.artemis-problem-statement') ?? document.body;
}

/**
 * Splits an authored reference list on top-level commas only, mirroring `TestReferenceParser` on the server.
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

function taskToken(index: number, name: string, references: string[]): string {
    return `#task(index=${index}, name=${name}, references=[${references.join('|')}])`;
}

/**
 * The tests the server bound a task to, read back from the feedback payload it renders. The fixtures are generated
 * with exactly one feedback entry per distinct authored reference, whose `testName` *is* that reference, so this list
 * is directly comparable to the reference list the legacy pipeline prints literally. A dropped, reordered or
 * misresolved reference therefore still fails the gate.
 */
function renderedTaskReferences(element: Element): string[] {
    const feedbackIds = element.getAttribute('data-feedback');
    if (!feedbackIds) {
        return [];
    }
    // The task names the ids it can show; the entries live once on the container, so the names are resolved from there.
    const raw = element.closest('.artemis-problem-statement')?.getAttribute('data-feedback');
    if (!raw) {
        return [];
    }
    const entries: unknown = JSON.parse(raw);
    if (typeof entries !== 'object' || entries === null || Array.isArray(entries)) {
        return [];
    }
    const byTestId = entries as Record<string, unknown>;
    return feedbackIds.split(',').map((id) => {
        const entry = byTestId[id.trim()];
        return typeof entry === 'object' && entry !== null && 'name' in entry ? String((entry as { name: unknown }).name) : '';
    });
}

/**
 * The markup the server renderer owns on top of the task itself: `buildTaskHtml` always appends a `<br>` to the task
 * span, and the pretty-printer may put indentation between the two. Returns an empty list when no such `<br>` follows,
 * so nothing is ever removed on the legacy side (which has no task element at all) or around an authored line break
 * that is not preceded by a task.
 */
function trailingRendererBreak(element: Element): ChildNode[] {
    const skipped: ChildNode[] = [];
    let sibling: ChildNode | null = element.nextSibling;
    while (sibling && sibling.nodeType === Node.TEXT_NODE && !(sibling.textContent ?? '').trim()) {
        skipped.push(sibling);
        sibling = sibling.nextSibling;
    }
    if (sibling && sibling.nodeType === Node.ELEMENT_NODE && (sibling as Element).tagName === 'BR') {
        skipped.push(sibling);
        return skipped;
    }
    return [];
}

function replaceLiteralTaskMarkers(node: Text, startIndex: number): number {
    const document = node.ownerDocument;
    let index = startIndex;
    let remainder = node;
    for (;;) {
        const match = singleTaskRegex.exec(remainder.data);
        if (!match) {
            return index;
        }
        const tail = remainder.splitText(match.index + match[0].length);
        const marker = remainder.splitText(match.index);
        marker.replaceWith(createSentinel(document, taskToken(index, match[1], splitTestReferences(match[2] ?? ''))));
        index++;
        remainder = tail;
    }
}

/**
 * Canonicalizer "task": the broadest of the set, and unavoidably so. The legacy pipeline escapes the `[task]` syntax
 * and prints it literally, while the server expands it into a span carrying an icon, the name and a stats line. Both
 * collapse to one sentinel carrying the task's name, its authored references and its position in the document.
 *
 * Deliberately narrow: only the literal marker (or the `.artemis-task` element plus the `<br>` the renderer emits with
 * it) is replaced. The containing `<p>` or `<li>` is untouched, so prose before and after a task, the nesting depth it
 * sits at and its position among its siblings all still have to agree.
 */
function canonicalizeTasks(root: Element): void {
    const document = root.ownerDocument;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    // Collected in document order across both representations first, so the sentinel index is the task's real position
    // even if a rendering ever contained both forms.
    const occurrences: Node[] = [];
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (node.nodeType === Node.ELEMENT_NODE && (node as Element).classList.contains('artemis-task')) {
            occurrences.push(node);
        } else if (node.nodeType === Node.TEXT_NODE && singleTaskRegex.test((node as Text).data) && !node.parentElement?.closest('.artemis-task')) {
            // A text node inside a rendered task is part of the subtree the element branch already replaces. Collecting
            // it too would leave a detached node behind and shift every later task's index by one.
            occurrences.push(node);
        }
    }

    let index = 0;
    for (const occurrence of occurrences) {
        if (occurrence.nodeType === Node.TEXT_NODE) {
            index = replaceLiteralTaskMarkers(occurrence as Text, index);
            continue;
        }
        const element = occurrence as Element;
        const trailing = trailingRendererBreak(element);
        element.replaceWith(createSentinel(document, taskToken(index, element.getAttribute('data-task-name') ?? '', renderedTaskReferences(element))));
        trailing.forEach((node) => node.remove());
        index++;
    }
}

/**
 * Canonicalizer "diagram": the legacy pipeline emits an empty container that a later HTTP round trip fills in, the
 * server emits the finished inline SVG. Both collapse to an *indexed* sentinel rather than being deleted, so the
 * number of diagrams, their order and their position in the document still have to agree.
 */
function canonicalizeDiagrams(root: Element): void {
    root.querySelectorAll('div.artemis-diagram, div[id^="plantUml-"]').forEach((container, index) => {
        container.replaceWith(createSentinel(container.ownerDocument, `#diagram(index=${index})`));
    });
}

/**
 * Canonicalizer "code": the legacy pipeline highlights during markdown conversion, so its code blocks already contain
 * `<span class="hljs-...">` elements and the `hljs` class. The raw server fixture contains neither, because
 * highlighting moved into the shadow-root content component (layer 3 asserts that separately). Both sides collapse to
 * the declared language plus the exact code text, whitespace included.
 */
function canonicalizeCodeBlocks(root: Element): void {
    root.querySelectorAll('pre > code').forEach((code) => {
        const language = [...code.classList].find((name) => name.startsWith(LANGUAGE_CLASS_PREFIX))?.slice(LANGUAGE_CLASS_PREFIX.length);
        code.replaceWith(createSentinel(code.ownerDocument, `#code(language=${language ?? 'none'})`, code.textContent ?? ''));
    });
}

function formulaToken(latex: string, displayMode: boolean): string {
    return `#formula(display=${displayMode}, tex=${latex.trim()})`;
}

/**
 * The TeX source KaTeX keeps in its MathML annotation. DOMPurify drops the `<annotation>` element but keeps its text,
 * so the source survives as the direct text content of the `<math>` element. Taking it from there rather than from the
 * rendered `textContent` is what makes the comparison about the authored formula instead of about KaTeX's layout.
 */
function renderedFormulaTex(element: Element): string {
    const math = element.querySelector('math');
    if (!math) {
        return '';
    }
    return [...math.childNodes]
        .filter((node) => node.nodeType === Node.TEXT_NODE)
        .map((node) => node.textContent ?? '')
        .join('');
}

/**
 * Canonicalizer "formula": the legacy pipeline renders KaTeX during markdown conversion and emits its full markup;
 * the server emits an inert `.katex-formula` placeholder that the content component renders later. Both collapse to
 * the authored TeX plus the display mode.
 */
function canonicalizeFormulas(root: Element): void {
    root.querySelectorAll('span.katex-formula').forEach((placeholder) => {
        const token = formulaToken(placeholder.getAttribute('data-formula') ?? '', placeholder.getAttribute('data-display-mode') === 'true');
        placeholder.replaceWith(createSentinel(placeholder.ownerDocument, token));
    });
    // `.katex-display` wraps a `.katex`, so the inner one must not be canonicalized a second time.
    [...root.querySelectorAll('.katex-display, .katex')]
        .filter((element) => !element.parentElement?.closest('.katex-display'))
        .forEach((element) => {
            const token = formulaToken(renderedFormulaTex(element), element.classList.contains('katex-display'));
            element.replaceWith(createSentinel(element.ownerDocument, token));
        });
}

/**
 * Canonicalizer "absolute URL": `MarkdownRelativeToAbsolutePathAttributeProvider` turns a root-relative `href`/`src`
 * into an absolute one against the configured `server.url`; the legacy pipeline leaves it relative. Only that one
 * exact origin is stripped, and only as a prefix of the whole value, so two different foreign origins still compare
 * as two different destinations. It runs on both sides, because a statement may also *author* an absolute link to the
 * Artemis instance itself, which only one side would otherwise shorten.
 */
function canonicalizeUrls(root: Element): void {
    root.querySelectorAll('[href], [src]').forEach((element) => {
        for (const attribute of ['href', 'src']) {
            const value = element.getAttribute(attribute);
            if (value?.startsWith(SERVER_ORIGIN + '/')) {
                element.setAttribute(attribute, value.slice(SERVER_ORIGIN.length));
            }
        }
    });
}

function comparedClasses(element: Element): string[] {
    return [...element.classList].filter((name) => COMPARED_MARKUP.classes.some((compared) => compared === name) || name.startsWith(COMPARED_MARKUP.classPrefix)).sort();
}

function startTag(element: Element): string {
    const parts = COMPARED_MARKUP.attributes.filter((name) => element.hasAttribute(name)).map((name) => `${name}="${element.getAttribute(name)}"`);
    const classes = comparedClasses(element);
    if (classes.length) {
        parts.unshift(`class="${classes.join(' ')}"`);
    }
    return `<${element.tagName.toLowerCase()}${parts.length ? ' ' + parts.join(' ') : ''}>`;
}

/**
 * Canonicalizer "whitespace": whitespace that touches a block boundary is not rendered, and the server pretty-prints
 * while the legacy pipeline does not. Such nodes are dropped; every other run of whitespace collapses to a single
 * space, which keeps the significant spacing between inline elements comparable.
 */
function isInsignificantWhitespace(node: Text): boolean {
    const atBlockBoundary = (sibling: ChildNode | null): boolean => !sibling || (sibling.nodeType === Node.ELEMENT_NODE && BLOCK_TAGS.has((sibling as Element).tagName));
    return atBlockBoundary(node.previousSibling) || atBlockBoundary(node.nextSibling);
}

function appendTextToken(tokens: string[], node: Text, preserveWhitespace: boolean): void {
    if (preserveWhitespace) {
        if (node.data !== '') {
            tokens.push(`text-exact:${node.data}`);
        }
        return;
    }
    if (!node.data.trim()) {
        if (node.data !== '' && !isInsignificantWhitespace(node)) {
            tokens.push('text: ');
        }
        return;
    }
    tokens.push(`text:${node.data.replace(/\s+/g, ' ')}`);
}

/**
 * A start-tag / text / end-tag stream, deliberately not a per-element `textContent` map: `textContent` repeats every
 * descendant's text at every ancestor, so a single changed word shows up as a diff on every enclosing element and the
 * position of the change is lost.
 */
function tokenize(root: Element): string[] {
    const tokens: string[] = [];
    const visit = (parent: Node, preserveWhitespace: boolean): void => {
        parent.childNodes.forEach((child) => {
            if (child.nodeType === Node.TEXT_NODE) {
                appendTextToken(tokens, child as Text, preserveWhitespace);
                return;
            }
            if (child.nodeType !== Node.ELEMENT_NODE) {
                // Comments and processing instructions carry no rendered content.
                return;
            }
            const element = child as Element;
            if (element.tagName === SENTINEL_TAG.toUpperCase()) {
                tokens.push(element.getAttribute(TOKEN_ATTRIBUTE) ?? '');
                const exactText = element.getAttribute(EXACT_TEXT_ATTRIBUTE);
                if (exactText !== null) {
                    tokens.push(`text-exact:${exactText}`);
                }
                return;
            }
            tokens.push(startTag(element));
            visit(element, preserveWhitespace || element.tagName === 'PRE' || element.tagName === 'CODE');
            tokens.push(`</${element.tagName.toLowerCase()}>`);
        });
    };
    visit(root, false);
    return tokens;
}

function canonicalRoot(html: string): Element {
    const root = problemStatementRoot(html);
    // Order matters: the code canonicalizer must consume fenced blocks before the task walk, which rewrites every text
    // node that matches the task syntax. The other way round, a statement documenting `[task][name](refs)` inside a
    // fenced block would have that text replaced by a sentinel element, and the code canonicalizer's `textContent`
    // would then silently drop it from the compared token.
    canonicalizeCodeBlocks(root);
    canonicalizeTasks(root);
    canonicalizeDiagrams(root);
    canonicalizeFormulas(root);
    canonicalizeUrls(root);
    return root;
}

/** The canonical token stream of a rendering, produced identically for both pipelines. */
function canonicalTokens(html: string): string[] {
    return tokenize(canonicalRoot(html));
}

// ---------------------------------------------------------------------------------------------------------------
// Layer 4 extractors: alerts, link destinations and image sources, read *without* the layer-1 canonicalizers so the
// signal the alert / autolink / relative-path work adds is asserted on its own and not merely inside a token stream.
// ---------------------------------------------------------------------------------------------------------------

function urlCanonicalizedRoot(html: string): Element {
    const root = problemStatementRoot(html);
    canonicalizeUrls(root);
    return root;
}

function linkDestinations(html: string): string[] {
    return [...urlCanonicalizedRoot(html).querySelectorAll('a[href]')].map((anchor) => anchor.getAttribute('href') ?? '');
}

function imageSources(html: string): string[] {
    return [...urlCanonicalizedRoot(html).querySelectorAll('img[src]')].map(
        (image) => `${image.getAttribute('src')}|${image.getAttribute('alt') ?? ''}|${image.getAttribute('title') ?? ''}`,
    );
}

function alertTypes(html: string): string[] {
    return [...urlCanonicalizedRoot(html).querySelectorAll('.markdown-alert')].map((alert) =>
        [...alert.classList].filter((name) => name.startsWith(COMPARED_MARKUP.classPrefix) && name !== 'markdown-alert-title').join(' '),
    );
}

/** Code blocks that carry the `hljs` class, which is what the legacy highlight.js integration adds. */
function highlightedCodeBlocks(html: string): number {
    return problemStatementRoot(html).querySelectorAll('pre code.hljs, pre .hljs').length;
}

/** Translates the legacy states into the server's status vocabulary so both sides compare as strings. */
const LEGACY_TO_SERVER: Record<TestCaseState, string> = {
    [TestCaseState.SUCCESS]: 'success',
    [TestCaseState.FAIL]: 'fail',
    [TestCaseState.NOT_EXECUTED]: 'not-executed',
    [TestCaseState.NO_RESULT]: 'no-result',
};

function serverStatuses(html: string): string[] {
    const document = new DOMParser().parseFromString(html, 'text/html');
    return [...document.querySelectorAll('.artemis-task')].map((element) => element.getAttribute('data-test-status') ?? '');
}

/** The authored reference list of each task, in document order. */
function authoredRefLists(markdown: string): string[] {
    return [...markdown.matchAll(taskRegex)].map((match) => match[2] ?? '');
}

describe('problem statement rendering parity', () => {
    let instructionService: ProgrammingExerciseInstructionService;
    let extensions: MarkdownItPlugin[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ProgrammingExerciseInstructionService,
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                // Stubbed: the wrapper transitively needs HttpClient and ThemeService, and the diagram canonicalizer
                // reduces both sides to an indexed sentinel anyway.
                { provide: ProgrammingExercisePlantUmlService, useValue: { getPlantUmlSvg: () => of('<svg></svg>'), getPlantUmlImage: () => of('') } },
                // Needed by the content component (layer 3) and by the legacy task-status component (the D1 test).
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: { open: vi.fn() } },
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

    // Zero corpus files would make every `it.each` below expand to zero tests, which a run reports as green. The Java
    // half (ProblemStatementRenderingParityTest.corpus) asserts the same thing on its side.
    it('has a non-empty corpus', () => {
        expect(files.length).toBeGreaterThan(0);
    });

    /**
     * Rebuilds exactly the test data the fixtures were generated with: one test case per distinct authored reference,
     * numbered in first-appearance order.
     */
    const testCasesFor = (markdown: string): ProgrammingExerciseTestCase[] => {
        const distinctRefs: string[] = [];
        for (const refList of authoredRefLists(markdown)) {
            for (const ref of splitTestReferences(refList)) {
                if (!distinctRefs.includes(ref)) {
                    distinctRefs.push(ref);
                }
            }
        }
        return distinctRefs.map((ref, index) => ({ id: index + 1, testName: ref }) as ProgrammingExerciseTestCase);
    };

    /**
     * The fixture's statement fragment after the passes the app runs over it before it reaches the frame.
     *
     * Deliberately stops short of the frame-specific steps (stripping `data-feedback` / `data-result`, rewriting
     * same-origin images): those are about what the sandbox may see, not about whether the two renderers agree,
     * and the canonicalizers below read the feedback payload to recover a task's test names.
     */
    const afterClientRendering = (serverHtml: string): string => {
        // The same fragment the app hands the assembler (programming-exercise-instruction-ssr.component.ts).
        const fragment = new DOMParser().parseFromString(serverHtml, 'text/html').querySelector('.artemis-problem-statement');
        if (!fragment) {
            return '';
        }
        const sanitized = new DOMParser().parseFromString(sanitizeFragment(fragment.outerHTML), 'text/html').querySelector('.artemis-problem-statement');
        if (!sanitized) {
            return '';
        }
        renderFormulas(sanitized);
        highlightCodeBlocks(sanitized);
        return sanitized.outerHTML;
    };

    /** The legacy engine's status per task, in document order, translated into the server's vocabulary. */
    const legacyStatuses = (markdown: string, testCases: ProgrammingExerciseTestCase[], result: Result): string[] =>
        authoredRefLists(markdown).map((refList) => {
            const testIds = instructionService.convertTestListToIds(refList, testCases);
            return LEGACY_TO_SERVER[instructionService.testStatusForTask(testIds, result).testCaseState];
        });

    // -----------------------------------------------------------------------------------------------------------
    // Layer 1: the whole rendering, compared exactly after the named canonicalizers above.
    // -----------------------------------------------------------------------------------------------------------

    it.each(files)('server and legacy renderings canonicalize to the same token stream for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);
        const server = canonicalTokens(serverHtml);

        // Two empty streams compare equal, so a refactor that made the canonicalizers swallow the whole document would
        // otherwise turn this into the vacuous gate it exists to replace. Every corpus file renders far more than this.
        expect(server.length).toBeGreaterThan(10);
        expect(server).toEqual(canonicalTokens(htmlForMarkdown(markdown, extensions)));
    });

    // -----------------------------------------------------------------------------------------------------------
    // Layer 2: task status. Retained as dedicated assertions because the layer-1 sentinel deliberately drops the
    // status (the legacy markdown pipeline never computes one), and because status has three arms worth covering.
    // -----------------------------------------------------------------------------------------------------------

    it.each(files)('server and legacy status engines agree for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);
        const testCases = testCasesFor(markdown);
        const result = { id: 1, successful: true, feedbacks: testCases.map((testCase) => ({ testCase, positive: true })) } as Result;

        expect(serverStatuses(serverHtml)).toEqual(legacyStatuses(markdown, testCases, result));
    });

    // The all-passing case above cannot expose a drift in the fail / not-executed arms of the status engine, and both
    // engines have distinct logic there. The server's half of these two comparisons is
    // ProblemStatementRenderingParityTest.everyTaskReflectsTheOutcomeOfItsTests, which renders this same corpus with
    // the same two scenarios and asserts the same expectation per task. Task count and order still come from the
    // committed fixture, so a task appearing or disappearing on either side fails here too.
    it.each(files)('server and legacy status engines agree on an all-failing result for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);
        const testCases = testCasesFor(markdown);
        const result = { id: 1, successful: false, feedbacks: testCases.map((testCase) => ({ testCase, positive: false })) } as Result;

        expect(legacyStatuses(markdown, testCases, result)).toEqual(serverStatuses(serverHtml).map(() => 'fail'));
    });

    it.each(files)('server and legacy status engines agree on a not-executed result for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);
        const testCases = testCasesFor(markdown);
        // `positive: undefined` is the legacy spelling of the server's `passed: null`: the test is known but did not run.
        const result = { id: 1, successful: false, feedbacks: testCases.map((testCase) => ({ testCase, positive: undefined })) } as Result;

        expect(legacyStatuses(markdown, testCases, result)).toEqual(serverStatuses(serverHtml).map(() => 'not-executed'));
    });

    // -----------------------------------------------------------------------------------------------------------
    // Layer 3: the passes that only exist after the content component has run.
    // -----------------------------------------------------------------------------------------------------------

    it('highlights every code block the legacy pipeline highlights, once the content component has run', () => {
        const { markdown, serverHtml } = readPair('markdown-features.md');
        const legacyHighlighted = highlightedCodeBlocks(htmlForMarkdown(markdown, extensions));

        // Syntax highlighting is a *client-side* pass: no Java highlighter matches highlight.js' language coverage, and
        // the hljs theme stylesheets cannot reach into the shadow root the markup is rendered in, so the content
        // component highlights the server's code blocks itself. The raw fixture therefore contains zero highlighted
        // blocks by design. Do not "fix" this measurement back onto the raw fixture.
        expect(legacyHighlighted).toBeGreaterThan(0);
        expect(highlightedCodeBlocks(serverHtml)).toBe(0);
        expect(highlightedCodeBlocks(afterClientRendering(serverHtml))).toBe(legacyHighlighted);
    });

    it('produces the same highlight.js markup as the legacy pipeline for a known language', () => {
        // The assertion above counts highlighted blocks, which cannot see *which* branch either side took. The content
        // component transcribes the three branches of `highlightWithHljs` (foundation/util/markdown.conversion.util.ts)
        // instead of sharing them, because the legacy unknown-language branch escapes the source itself while the SSR
        // one relies on the server having escaped it already. The known-language branch is the one that produces token
        // markup at all, and it is comparable byte for byte, so a drift in branch selection or in the highlighting call
        // fails here. The other two branches diverge legitimately in where the escaping comes from.
        const source = 'class Example {\n    List<String> run() { return null; }\n}\n';
        // The server escapes the code before it reaches the client; the content component reads it back via textContent.
        const escaped = 'class Example {\n    List&lt;String&gt; run() { return null; }\n}\n';
        const legacyCode = problemStatementRoot(htmlForMarkdown('```java\n' + source + '```')).querySelector('pre > code');
        const renderedFragment = `<div class="artemis-problem-statement"><pre><code class="language-java">${escaped}</code></pre></div>`;
        const renderedCode = problemStatementRoot(afterClientRendering(renderedFragment)).querySelector('pre > code');

        // Two nulls would compare equal, and unhighlighted source would too, so both are ruled out first.
        expect(legacyCode?.innerHTML).toContain('hljs-keyword');
        expect(renderedCode?.innerHTML).toContain('hljs-keyword');
        expect(renderedCode?.innerHTML).toBe(legacyCode?.innerHTML);
    });

    it('renders the same math as the legacy pipeline, once the content component has run', () => {
        // The server emits inert `.katex-formula` placeholders (MathFormulaExtractor.restore) and strips its own KaTeX
        // script, so the math only exists after the content component has rendered it.
        const placeholder = '<div class="artemis-problem-statement"><p><span class="katex-formula" data-formula="a^2 + b^2" data-display-mode="false"></span></p></div>';
        const legacy = problemStatementRoot(htmlForMarkdown('$a^2 + b^2$'));
        const rendered = problemStatementRoot(afterClientRendering(placeholder));

        // Without these two, the comparison passes when *neither* side renders math: two empty node lists have the
        // same length, and `undefined` equals `undefined`. The neighbouring highlighting tests guard the same way.
        expect(legacy.querySelectorAll('.katex').length).toBeGreaterThan(0);
        expect(legacy.querySelector('.katex-html')?.textContent).toBeTruthy();
        expect(rendered.querySelectorAll('.katex')).toHaveLength(legacy.querySelectorAll('.katex').length);
        expect(rendered.querySelector('.katex-html')?.textContent).toBe(legacy.querySelector('.katex-html')?.textContent);
    });

    // -----------------------------------------------------------------------------------------------------------
    // Layer 4: alerts, link destinations and image sources on their own, so the signal stays visible even where a
    // layer-1 canonicalizer could otherwise absorb it.
    // -----------------------------------------------------------------------------------------------------------

    it.each(files)('server and legacy agree on every link destination for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);

        expect(linkDestinations(serverHtml)).toEqual(linkDestinations(htmlForMarkdown(markdown, extensions)));
    });

    it.each(files)('server and legacy agree on every image source for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);

        expect(imageSources(serverHtml)).toEqual(imageSources(htmlForMarkdown(markdown, extensions)));
    });

    it.each(files)('server and legacy agree on every alert for %s', (name) => {
        const { markdown, serverHtml } = readPair(name);

        expect(alertTypes(serverHtml)).toEqual(alertTypes(htmlForMarkdown(markdown, extensions)));
    });

    // The corpus is only a gate while it still exercises the features. Removing the fenced code block, the alert, the
    // bare URL or the links / images from the corpus would silently make several assertions above vacuous.
    it('keeps the corpus exercising the features the gate is about', () => {
        const featureHtml = readPair('markdown-features.md').serverHtml;
        const structureHtml = readPair('inline-structure.md').serverHtml;

        expect(problemStatementRoot(featureHtml).querySelectorAll('pre code').length).toBeGreaterThan(0);
        expect(problemStatementRoot(featureHtml).querySelectorAll('table').length).toBeGreaterThan(0);
        expect(alertTypes(featureHtml)).not.toHaveLength(0);
        expect(linkDestinations(featureHtml)).not.toHaveLength(0);
        expect(linkDestinations(structureHtml).length).toBeGreaterThan(1);
        expect(imageSources(structureHtml).length).toBeGreaterThan(1);
        expect(problemStatementRoot(structureHtml).querySelectorAll('ol ul li').length).toBeGreaterThan(0);
        expect(problemStatementRoot(readPair('name-based-tasks.md').serverHtml).querySelectorAll('.artemis-diagram').length).toBeGreaterThan(0);
    });
});

// -------------------------------------------------------------------------------------------------------------
// Focused canonicalizer tests. Every canonicalizer above hides one named difference; each test below proves it
// hides that difference *and nothing else*, which is the only thing that keeps the gate from passing vacuously.
// -------------------------------------------------------------------------------------------------------------

describe('problem statement parity canonicalizers', () => {
    /**
     * The server's document shell. `feedback` is the document-level payload the renderer puts on the container: task
     * elements name the test ids they can show and resolve the entries from here, so a fixture asserting on a task's
     * references has to carry it.
     */
    const server = (body: string, feedback?: string) =>
        `<!DOCTYPE html><html><body class="artemis-ssr-body"><div class="artemis-problem-statement"${
            feedback ? ` data-feedback="${feedback.replace(/"/g, '&quot;')}"` : ''
        }>${body}</div></body></html>`;

    /** The document-level payload for a single test, as the renderer emits it. */
    const documentFeedback = (testId: number, name: string, passed = true) => JSON.stringify({ [testId]: { name, passed } });

    describe('container', () => {
        it('hides the server document shell and its statement wrapper', () => {
            expect(canonicalTokens(server('<p>Hello</p>'))).toEqual(canonicalTokens('<p>Hello</p>'));
        });

        it('does not hide a difference inside the wrapper', () => {
            expect(canonicalTokens(server('<p>Hello</p>'))).not.toEqual(canonicalTokens('<p>Goodbye</p>'));
        });
    });

    describe('task', () => {
        const legacyTask = '<ul><li>Outer<ul><li>Prefix text [task][Validate](testValidate()) suffix text.</li></ul></li></ul>';
        const serverTask =
            '<ul><li>Outer<ul><li>Prefix text <span class="artemis-task artemis-task-success" data-task-name="Validate" data-test-ids="2"' +
            ' data-test-status="success" data-feedback="2">' +
            '<i class="fa fa-check-circle"></i> Validate <span class="artemis-task-stats">1 of 1 tests passed</span></span>\n  <br>\n  suffix text.</li></ul></li></ul>';
        /** The task above resolves its single reference through this, exactly as a rendered document does. */
        const serverTaskFeedback = documentFeedback(2, 'testValidate()');
        const serverDocument = (body = serverTask) => server(body, serverTaskFeedback);

        it('hides the literal marker against the rendered task subtree, inside a nested list', () => {
            expect(canonicalTokens(serverDocument())).toEqual(canonicalTokens(legacyTask));
        });

        it('keeps the text around a task, so it cannot swallow the containing paragraph or list item', () => {
            const tokens = canonicalTokens(legacyTask);

            expect(tokens).toContain('text:Prefix text ');
            expect(tokens).toContain('text: suffix text.');
            expect(tokens.filter((token) => token.startsWith('#task('))).toEqual(['#task(index=0, name=Validate, references=[testValidate()])']);
            // The nesting the task sits at survives: outer list item, inner list, inner list item.
            expect(tokens.slice(0, 5)).toEqual(['<ul>', '<li>', 'text:Outer', '<ul>', '<li>']);
        });

        it('fails when the prose around a task differs', () => {
            expect(canonicalTokens(serverDocument())).not.toEqual(canonicalTokens(legacyTask.replace('suffix text', 'other text')));
        });

        it('fails when a task moves to a different position in the document', () => {
            const moved = '<ul><li>Outer<ul><li>Prefix text suffix text. [task][Validate](testValidate())</li></ul></li></ul>';

            expect(canonicalTokens(moved)).not.toEqual(canonicalTokens(legacyTask));
        });

        it('fails when the task name or its references differ', () => {
            expect(canonicalTokens(legacyTask)).not.toEqual(canonicalTokens(legacyTask.replace('[Validate]', '[Verify]')));
            expect(canonicalTokens(legacyTask)).not.toEqual(canonicalTokens(legacyTask.replace('testValidate()', 'testValidate(),testMore()')));
        });

        it('removes only the renderer-owned line break, not an authored one', () => {
            const withAuthoredBreak = serverDocument(serverTask.replace('<br>\n  suffix', '<br><br>\n  suffix'));

            expect(canonicalTokens(withAuthoredBreak)).toEqual(canonicalTokens(legacyTask.replace(' suffix', '<br> suffix')));
        });

        it('does not reach into a fenced code block that documents the task syntax', () => {
            // The code canonicalizer runs first for exactly this reason: rewriting the marker here would remove it from
            // the code element's `textContent`, and the region would drop out of the comparison on both sides at once.
            const documented = '<pre><code class="language-markdown">[task][Sort](testSort())\n</code></pre>';

            expect(canonicalTokens(server(documented))).toEqual(['<pre>', '#code(language=markdown)', 'text-exact:[task][Sort](testSort())\n', '</pre>']);
            expect(canonicalTokens(server(documented))).not.toEqual(canonicalTokens(server(documented.replace('[task][Sort](testSort())', '[task][Sort](testOther())'))));
        });
    });

    describe('diagram', () => {
        it('hides the empty legacy container against the inline server SVG', () => {
            const legacy = '<p>Before</p><div class="mb-4" id="plantUml-7-0"></div><p>After</p>';
            const rendered = '<p>Before</p><div class="artemis-diagram" data-diagram-id="uml-0"><svg><path d="M0 0"></path></svg></div><p>After</p>';

            expect(canonicalTokens(server(rendered))).toEqual(canonicalTokens(legacy));
        });

        it('keeps the count, order and position of the diagrams comparable', () => {
            const one = '<div class="mb-4" id="plantUml-0"></div>';
            const two = one + '<p>Between</p>' + '<div class="mb-4" id="plantUml-1"></div>';

            expect(canonicalTokens(two)).toEqual(['#diagram(index=0)', '<p>', 'text:Between', '</p>', '#diagram(index=1)']);
            expect(canonicalTokens(one)).not.toEqual(canonicalTokens(two));
            expect(canonicalTokens(two)).not.toEqual(canonicalTokens('<p>Between</p>' + one + '<div class="mb-4" id="plantUml-1"></div>'));
        });
    });

    describe('code block', () => {
        const source = 'class Example {\n    void run() {}\n}\n';
        const legacy = `<pre><code class="hljs language-java"><span class="hljs-keyword">class</span> Example {\n    void run() {}\n}\n</code></pre>`;
        const rendered = `<pre><code class="language-java">${source}</code></pre>`;

        it('hides the highlight.js markup and the hljs class', () => {
            expect(canonicalTokens(server(rendered))).toEqual(canonicalTokens(legacy));
        });

        it('preserves the code whitespace byte-exactly', () => {
            expect(canonicalTokens(server(rendered))).toEqual(['<pre>', '#code(language=java)', `text-exact:${source}`, '</pre>']);
            expect(canonicalTokens(server(rendered))).not.toEqual(canonicalTokens(server(`<pre><code class="language-java">${source.replace('    ', '  ')}</code></pre>`)));
        });

        it('fails when the declared language or the code text differs', () => {
            expect(canonicalTokens(server(rendered))).not.toEqual(canonicalTokens(server(rendered.replace('language-java', 'language-python'))));
            expect(canonicalTokens(server(rendered))).not.toEqual(canonicalTokens(server(rendered.replace('void run', 'void walk'))));
        });
    });

    describe('formula', () => {
        it('hides the rendered KaTeX markup against the inert server placeholder', () => {
            const legacy = htmlForMarkdown('Inline $a^2 + b^2$ math.');
            const rendered = server('<p>Inline <span class="katex-formula" data-formula="a^2 + b^2" data-display-mode="false"></span> math.</p>');

            expect(canonicalTokens(rendered)).toEqual(canonicalTokens(legacy));
        });

        it('hides the display-mode wrapper the legacy pipeline adds', () => {
            const legacy = htmlForMarkdown('$$\n\\frac{1}{2}\n$$');
            const rendered = server('<p><span class="katex-formula" data-formula="\\frac{1}{2}" data-display-mode="true"></span></p>');

            expect(canonicalTokens(rendered)).toEqual(canonicalTokens(legacy));
        });

        it('fails when the formula or its display mode differs', () => {
            const rendered = server('<p><span class="katex-formula" data-formula="a^2" data-display-mode="false"></span></p>');

            expect(canonicalTokens(rendered)).not.toEqual(canonicalTokens(htmlForMarkdown('$b^2$')));
            expect(canonicalTokens(rendered)).not.toEqual(canonicalTokens(server(rendered.replace('data-display-mode="false"', 'data-display-mode="true"'))));
        });
    });

    describe('absolute URL', () => {
        it('hides the server-side absolutization of a root-relative destination', () => {
            const legacy = '<p><a href="/api/core/files/markdown/a.pdf">doc</a><img src="/api/core/files/markdown/b.png" alt="b"></p>';
            const rendered = `<p><a href="${SERVER_ORIGIN}/api/core/files/markdown/a.pdf">doc</a><img src="${SERVER_ORIGIN}/api/core/files/markdown/b.png" alt="b"></p>`;

            expect(canonicalTokens(server(rendered))).toEqual(canonicalTokens(legacy));
        });

        it('strips only the exact configured origin, so two wrong foreign destinations still differ', () => {
            expect(canonicalTokens('<p><a href="https://a.example.org/x">l</a></p>')).not.toEqual(canonicalTokens('<p><a href="https://b.example.org/x">l</a></p>'));
            // A host that merely starts with the configured origin is a different host and must not be shortened.
            expect(canonicalTokens(`<p><a href="${SERVER_ORIGIN}.evil.test/x">l</a></p>`)).not.toEqual(canonicalTokens('<p><a href="/x">l</a></p>'));
        });

        it('fails when the destination itself differs', () => {
            expect(canonicalTokens(`<p><a href="${SERVER_ORIGIN}/a">l</a></p>`)).not.toEqual(canonicalTokens('<p><a href="/b">l</a></p>'));
        });
    });

    describe('whitespace', () => {
        it('hides the server pretty-printer indentation between block elements', () => {
            expect(canonicalTokens(server('<ul>\n <li>One</li>\n <li>Two</li>\n</ul>'))).toEqual(canonicalTokens('<ul><li>One</li><li>Two</li></ul>'));
        });

        it('keeps the significant space between two inline elements', () => {
            expect(canonicalTokens('<p><em>a</em> <em>b</em></p>')).not.toEqual(canonicalTokens('<p><em>a</em><em>b</em></p>'));
        });

        it('collapses a run of whitespace inside prose but does not delete it', () => {
            expect(canonicalTokens('<p>a\n   b</p>')).toEqual(canonicalTokens('<p>a b</p>'));
            expect(canonicalTokens('<p>a b</p>')).not.toEqual(canonicalTokens('<p>ab</p>'));
        });
    });

    describe('attribute allowlist', () => {
        it('does not fail on a difference in an unlisted attribute', () => {
            // class="table" is exactly the case that forced the allowlist: MarkdownitTagClass adds it, the server does not.
            expect(canonicalTokens('<table class="table"><tr><td>a</td></tr></table>')).toEqual(canonicalTokens('<table><tr><td>a</td></tr></table>'));
            expect(canonicalTokens('<p style="color:red" id="generated-3" data-svg-index="0">a</p>')).toEqual(canonicalTokens('<p>a</p>'));
            expect(canonicalTokens('<span class="artemis-task-stats octicon mb-4">a</span>')).toEqual(canonicalTokens('<span>a</span>'));
        });

        it('fails on a difference in a listed attribute', () => {
            for (const [left, right] of [
                ['<p><a href="/a">l</a></p>', '<p><a href="/b">l</a></p>'],
                ['<p><img src="/a.png" alt="a"></p>', '<p><img src="/a.png" alt="b"></p>'],
                ['<p><img src="/a.png" title="a"></p>', '<p><img src="/a.png"></p>'],
                ['<table><tr><td colspan="2">a</td></tr></table>', '<table><tr><td>a</td></tr></table>'],
                ['<table><tr><td rowspan="2">a</td></tr></table>', '<table><tr><td>a</td></tr></table>'],
                ['<ol start="3"><li>a</li></ol>', '<ol><li>a</li></ol>'],
                ['<ol type="a"><li>a</li></ol>', '<ol><li>a</li></ol>'],
            ]) {
                expect(canonicalTokens(left)).not.toEqual(canonicalTokens(right));
            }
        });

        it('fails on a difference in a semantic class', () => {
            expect(canonicalTokens('<div class="markdown-alert markdown-alert-note"><p>a</p></div>')).not.toEqual(
                canonicalTokens('<div class="markdown-alert markdown-alert-warning"><p>a</p></div>'),
            );
            expect(canonicalTokens('<div class="markdown-alert"><p class="markdown-alert-title">a</p></div>')).not.toEqual(
                canonicalTokens('<div class="markdown-alert"><p>a</p></div>'),
            );
            // Outside `pre > code`, which the code canonicalizer consumes before any class is compared.
            expect(canonicalTokens('<p><span class="hljs">a</span></p>')).not.toEqual(canonicalTokens('<p><span>a</span></p>'));
        });
    });

    describe('token stream shape', () => {
        it('emits start tags, text and end tags rather than per-element text content', () => {
            expect(canonicalTokens('<p>a<em>b</em>c</p>')).toEqual(['<p>', 'text:a', '<em>', 'text:b', '</em>', 'text:c', '</p>']);
        });

        it('distinguishes two renderings whose text content is identical but whose structure is not', () => {
            expect(canonicalTokens('<p><em>ab</em></p>')).not.toEqual(canonicalTokens('<p><em>a</em>b</p>'));
            expect(canonicalTokens('<ul><li>a</li><li>b</li></ul>')).not.toEqual(canonicalTokens('<ul><li>a</li></ul><ul><li>b</li></ul>'));
        });
    });
});

// -------------------------------------------------------------------------------------------------------------
// The D1 divergences: places where the server deliberately does not reproduce the legacy behaviour. Once the task
// subtree is canonicalized, the markdown layer cannot observe them at all, so they are asserted here against the
// legacy task *component* instead of being masked in the gate.
// -------------------------------------------------------------------------------------------------------------

describe('problem statement rendering: deliberate divergences from the legacy task component', () => {
    // The server half of each divergence below is measured, not restated here: a client spec cannot invoke the server
    // renderer, and an expectation written as a local constant would only compare itself. The two tests that do measure
    // it live in src/test/java/de/tum/cit/aet/artemis/exercise/ProblemStatementRenderingIntegrationTest.java:
    // shouldShowSuccessWhenAllTestsPassedWithoutFeedback (success plus "n of n tests passed", never the "No results"
    // text) and shouldKeepNoTestsWhenAllTestsPassedAndTaskHasNoRefs (no-tests, never success). What these tests add is
    // the other side: they fail the moment the *legacy* behaviour changes, which is what forces the divergence record
    // to be revisited rather than quietly going stale.

    const renderLegacyTask = (testIds: number[], latestResult: Result) => {
        const fixture = TestBed.createComponent(ProgrammingExerciseInstructionTaskStatusComponent);
        fixture.componentRef.setInput('taskName', 'Sort');
        fixture.componentRef.setInput('testIds', testIds);
        fixture.componentRef.setInput('exercise', {} as Exercise);
        fixture.componentRef.setInput('participation', {} as Participation);
        fixture.componentRef.setInput('latestResult', latestResult);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    };

    /** A rendered task span as the server emits it on the all-passed path: green, with a possibly empty id list. */
    const renderedTaskSpan = (name: string, testIds: string) =>
        `<span class="artemis-task artemis-task-success" data-task-name="${name}" data-test-ids="${testIds}" data-test-status="success">${name}</span>`;

    /**
     * The statement fragment inside the sandboxed frame, with interactivity switched on.
     *
     * Runs the real frame script against the markup, then sends it the same `interactive` message the content
     * component sends, so what is asserted below is the shipped behaviour rather than a stand-in for it. The
     * parent only ever names tasks that carry test ids, which is the gate this divergence is about.
     */
    const renderSsrContent = (body: string, tasks: SsrTask[]): Document => {
        const harness = runFrameScript(`<div class="artemis-problem-statement">${body}</div>`);
        harness.sendFromParent(interactiveMessage(tasks.filter((task) => task.testIds.length).map((task) => ({ index: task.index, label: `${task.taskName}: ${task.status}` }))));
        return harness.document;
    };

    const ssrTask = (index: number, taskName: string, testIds: number[]): SsrTask => ({
        index,
        taskName,
        testIds,
        status: 'success',
        authoredCount: 1,
        notExecutedCount: 0,
    });

    let taskExtension: MarkdownItPlugin;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ProgrammingExerciseInstructionService,
                ProgrammingExerciseTaskExtensionWrapper,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
        });
        taskExtension = TestBed.inject(ProgrammingExerciseTaskExtensionWrapper).getExtension();
    });

    it('divergence 1: legacy has no "no tests" concept for a task without references, the server does', () => {
        const instructionService = TestBed.inject(ProgrammingExerciseInstructionService);
        const successfulWithoutFeedback = { id: 1, successful: true, feedbacks: [] } as Result;

        // `testIds` is `[]`-truthy in the legacy engine, so a reference-less task takes the "everything passed" arm.
        // The server returns `no-tests` for the same input, a status the legacy vocabulary does not even contain.
        expect(instructionService.testStatusForTask([], successfulWithoutFeedback).testCaseState).toBe(TestCaseState.SUCCESS);

        const element = renderLegacyTask([], successfulWithoutFeedback);
        // Legacy renders a reference-less task as passed, and labels it "no tests" only through the same else-branch it
        // uses for a missing result. The server reports it as `no-tests` instead, which is the intended correction.
        expect(element.querySelector('.test-icon.text-success')).not.toBeNull();
        expect(element.textContent).toContain('artemisApp.editor.testStatusLabels.noTests');
    });

    it('divergence 2: legacy shows a green icon next to "No result", the server shows the stats line', () => {
        const successfulWithoutFeedback = { id: 1, successful: true, feedbacks: [] } as Result;

        const element = renderLegacyTask([1], successfulWithoutFeedback);

        // The contradiction: the icon says the task passed while the label says nothing is known about it, because the
        // label branch keys off "are there feedbacks" rather than off the computed state
        // (programming-exercise-instruction-task-status.component.html).
        expect(element.querySelector('.test-icon.text-success')).not.toBeNull();
        expect(element.textContent).toContain('artemisApp.editor.testStatusLabels.noResult');
        expect(element.textContent).not.toContain('artemisApp.editor.testStatusLabels.totalTestsPassing');
    });

    it('divergence 3: the legacy pipeline renders strikethrough as <s>, the server as <del>', () => {
        // Open, not intended: GFM specifies `<del>`, so the server (commonmark StrikethroughExtension) is right and the
        // legacy pipeline (markdown-it) is the outlier. Recorded here rather than in the corpus because the parity gate
        // would fail on it, and recorded as a test rather than as a README note so that closing it on either side turns
        // this red instead of leaving a stale prohibition behind. Aligning the two is a follow-up.
        //
        // This half only pins the legacy pipeline, and both halves are needed: on its own it cannot tell "the server
        // emits <del>" from "the server emits nothing at all", which is what a dropped safelist entry looks like. The
        // server half is `shouldRenderStrikethroughAsDel` in ProblemStatementRenderingIntegrationTest.
        expect(htmlForMarkdown('~~gone~~')).toContain('<s>gone</s>');
        expect(htmlForMarkdown('~~gone~~')).not.toContain('<del>gone</del>');
    });

    it('divergence 4: the legacy pipeline escapes task syntax inside a fenced code block, the server does not', () => {
        // Open, not intended: the legacy task extension rewrites the raw markdown before markdown-it tokenizes
        // (ArtemisTextReplacementPlugin), so it escapes the marker even inside a fenced block, and the backslashes are
        // then visible to the reader. The server masks code blocks before task expansion
        // (ProblemStatementRenderingService.maskCodeBlocks) and keeps the block verbatim, which is the correct
        // behaviour for a statement that documents the syntax. The corpus deliberately contains no such block, since
        // the gate would fail on it. Aligning the two is a follow-up.
        const fenced = '```\n[task][Sort](testSort())\n```';
        const code = problemStatementRoot(htmlForMarkdown(fenced, [taskExtension])).querySelector('pre > code');

        expect(code?.textContent).toBe('\\[task\\]\\[Sort\\]\\(testSort\\(\\)\\)\n');
    });

    it('divergence 5: on an all-passed result the server resolves only <testid> references, leaving name-only tasks inert', () => {
        // Open, not intended. On the all-passed path the request carries no test results at all
        // (ProblemStatementRenderingService.extractTasks), so `lookup.resolve` cannot resolve a *name*: only a
        // `<testid>` wrapper reaches `data-test-ids`, and a name-only task is rendered with an empty list. The client
        // then gates `role` / `tabindex` on `task.testIds.length` (content component) and drops the activation in
        // `ProgrammingExerciseInstructionSsrComponent.openTaskFeedback`. Two tasks with an identical green presentation
        // therefore end up one clickable and one inert, decided purely by how their tests were referenced.
        const frameDocument = renderSsrContent(renderedTaskSpan('ById', '1') + renderedTaskSpan('ByName', ''), [ssrTask(0, 'ById', [1]), ssrTask(1, 'ByName', [])]);
        const rendered = [...frameDocument.querySelectorAll('.artemis-task')];

        expect(rendered.map((element) => element.getAttribute('data-test-status'))).toEqual(['success', 'success']);
        expect(rendered.map((element) => element.getAttribute('role'))).toEqual(['button', null]);

        // The legacy engine never makes that distinction: `convertTestListToIds` maps every authored reference to an id
        // (or to -1 for a name it cannot find), so both reference styles keep a non-empty list, and the clickable stats
        // line is gated on the result's feedbacks instead. For this result that means neither task is clickable, which
        // is at least uniform. Resolving names server-side, or gating on the authored count, is a follow-up.
        const instructionService = TestBed.inject(ProgrammingExerciseInstructionService);
        const successfulWithoutFeedback = { id: 1, successful: true, feedbacks: [] } as Result;

        expect(instructionService.convertTestListToIds('<testid>1</testid>', [])).toEqual([1]);
        expect(instructionService.convertTestListToIds('testSort()', [])).toEqual([-1]);
        expect(renderLegacyTask([1], successfulWithoutFeedback).querySelector('.test-status--linked')).toBeNull();
        expect(renderLegacyTask([-1], successfulWithoutFeedback).querySelector('.test-status--linked')).toBeNull();
    });
});
