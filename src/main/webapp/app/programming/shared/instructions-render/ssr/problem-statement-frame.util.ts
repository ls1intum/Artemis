import DOMPurify from 'dompurify';
import katex from 'katex';
import hljs from 'app/foundation/util/highlight-languages.util';
import { SSR_TASK_STATUSES, SsrTask, SsrTaskStatus } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { FRAME_SCRIPT, GENERATION_PLACEHOLDER } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-script';
import { contentSecurityPolicy } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-policy';

/**
 * Turns the server's rendered document into the single HTML string that goes into the sandboxed frame.
 *
 * The frame is what makes a sanitizer bypass harmless: it has an opaque origin, so the markup inside it reaches
 * no cookie, no storage, no parent DOM and no authenticated API response, and it carries a CSP of its own whose
 * nonce only the trusted frame script has. Every function in this file exists to make sure that nothing which
 * survives into that frame is either sensitive or able to phone home.
 *
 * The order below is fixed and pinned by specs, because each step assumes the previous one:
 *
 *   extract fragment -> DOMPurify -> strip sensitive attributes -> block URL references
 *   -> rewrite same-origin images -> KaTeX -> highlight.js -> serialize
 *
 * KaTeX and highlight.js run last and in the parent rather than inside the frame. Both are pure string
 * producers (`renderToString`, `highlight().value`), so they need no live document, and keeping them here
 * means the frame needs no library and no second script.
 *
 * The three `innerHTML` assignments below are marked for the static analysers, and the reason is stronger than
 * the usual "the value is trusted": every one of them writes into a `DOMParser` document, which is inert. It is
 * attached to nothing, so no script in it can run and no resource in it is fetched, and the markup only ever
 * leaves as a string that goes into the sandboxed frame. The values are KaTeX's own output and highlight.js'
 * output over text read back from `textContent`, both of which escape what they emit.
 */

/** The `data-*` attributes the server writes that carry information about the viewer rather than the statement. */
const SENSITIVE_ATTRIBUTES = ['data-feedback', 'data-result'];

/**
 * SVG presentation attributes that accept a `url(...)` reference, mirroring
 * `SvgSanitizer.URL_BEARING_ATTRIBUTES`. These are not cosmetic: an external reference in them issues a real
 * network request. Measured in the sandboxed frame with its CSP active, `fill`, `stroke`, `clip-path` and
 * `mask` all fetched in Chromium, and `mask` fetched in Firefox and WebKit too.
 */
const URL_BEARING_SVG_ATTRIBUTES = ['fill', 'stroke', 'filter', 'clip-path', 'mask', 'marker-start', 'marker-mid', 'marker-end'];

/**
 * Elements that may not appear in the frame.
 *
 * The first group is what `SvgSanitizer` denies outright, forbidden here again in case the server safelist is
 * bypassed. `feimage` joins them for the same reason it is guarded there: an `feImage` inside a filter fetches its
 * `href`, verified in all three engines, and it is not obvious from reading the element that it does.
 *
 * The second group cannot be produced by the renderer at all: jsoup's `Safelist.relaxed()` permits none of them.
 * They are listed because each one is a fetch or a submission that the image rewriting below would not catch, and
 * forbidding an element the server can never emit costs nothing.
 */
const DENIED_ELEMENTS = [
    'script',
    'foreignobject',
    'use',
    'image',
    'animate',
    'animatetransform',
    'animatemotion',
    'set',
    'feimage',
    'video',
    'audio',
    'track',
    'source',
    'input',
    'button',
    'form',
    'object',
    'embed',
    'iframe',
];

/**
 * Attributes that make the browser fetch or submit to their value.
 *
 * Enumerated because `img[src]` is not the only one: `<video poster>`, `<input type=image src>`, `<form action>`
 * and SVG `href` all issue a request, and all of them were observed surviving into the frame before the element
 * list above was widened. This pass is the backstop for anything that still gets through.
 */
const URL_BEARING_ATTRIBUTES = ['src', 'srcset', 'href', 'xlink:href', 'poster', 'data', 'action', 'formaction'];

/** Matches a CSS comment, non-greedy across lines, as `SvgSanitizer.CSS_COMMENT_PATTERN` does. */
const CSS_COMMENT = /\/\*[\s\S]*?\*\//g;

/** Constructs that must never appear in a style value, mirroring `SvgSanitizer.DANGEROUS_CSS_PATTERN`. */
const DANGEROUS_CSS = /expression\s*\(|@import|-moz-binding/i;

/**
 * CSS functions that fetch an image without ever writing `url(`.
 *
 * `image-set("https://…" 1x)` takes a bare string as its source, and it really is fetched: verified in Chromium,
 * Firefox and WebKit, in every one of them. A rule that only scans for `url(` therefore misses it completely,
 * which is why these are rejected by name rather than by their argument. None of them appears in PlantUML output
 * or in any legitimate statement CSS, so rejecting the whole declaration costs nothing.
 */
const IMAGE_FUNCTIONS = /(^|[^a-z-])(-webkit-)?(image-set|cross-fade|image|element|paint)\s*\(/i;

const URL_REFERENCE = /url\s*\(/gi;

/** Marks a code block that has already been highlighted, so a second pass over retained markup is a no-op. */
const HIGHLIGHTED_MARKER = 'data-highlighted';

/** The class CommonMark puts on a fenced code block, and the only place the authored language survives. */
const LANGUAGE_CLASS_PREFIX = 'language-';

export interface AssembledFrame {
    /** The complete document for `iframe.srcdoc`. */
    srcdoc: string;
    /** Per-render token; a message that does not carry it belongs to a superseded frame and is dropped. */
    generation: string;
    /** The tasks of this document, in document order; index i belongs to the i-th `.artemis-task` element. */
    tasks: SsrTask[];
    /**
     * Every `href` that survived sanitization into the frame.
     *
     * The frame cannot open a link itself (no `allow-popups`, no `allow-top-navigation`), so it asks the parent
     * to. This list is what keeps that from being an open navigation primitive: the parent honours a request
     * only for a link that is actually in the document the reader is looking at, so the worst a forged message
     * can do is what the reader could have done by clicking.
     */
    linkTargets: string[];
}

/** 128 bits of randomness as lowercase hex. Hex only, so it never needs escaping into an attribute or a policy. */
function randomToken(): string {
    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    return [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * Whether a style or presentation-attribute value may be kept, mirroring `SvgSanitizer.isCssSafe`.
 *
 * Deliberately one degree stricter than the server in a single place: an unterminated `url(` is rejected here,
 * where the server's scan skips it for want of a closing parenthesis. No legitimate declaration contains one,
 * and this layer exists precisely for the case where the server's pass was bypassed.
 */
export function isCssValueSafe(value: string | null | undefined): boolean {
    if (!value) {
        return true;
    }
    const withoutComments = value.replace(CSS_COMMENT, '');
    if (DANGEROUS_CSS.test(withoutComments) || IMAGE_FUNCTIONS.test(withoutComments)) {
        return false;
    }
    // PlantUML never emits CSS escape sequences, so they can only be an attempt to obfuscate one of the above.
    if (withoutComments.includes('\\')) {
        return false;
    }
    URL_REFERENCE.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = URL_REFERENCE.exec(withoutComments)) !== null) {
        const open = withoutComments.indexOf('(', match.index);
        const close = withoutComments.indexOf(')', open);
        if (close <= open) {
            return false;
        }
        let url = withoutComments.slice(open + 1, close).trim();
        if ((url.startsWith("'") && url.endsWith("'")) || (url.startsWith('"') && url.endsWith('"'))) {
            url = url.slice(1, -1).trim();
        }
        // Only a local fragment reference stays; anything else is a fetch, which is what this rule exists to stop.
        if (!url.startsWith('#')) {
            return false;
        }
    }
    return true;
}

/**
 * Sanitizes the statement fragment with DOMPurify, extended by the URL rules above.
 *
 * DOMPurify's default configuration keeps `style` attributes and the SVG presentation attributes, which is
 * exactly the gap this hook closes. The rules are not a blanket strip: PlantUML emits inline `style` for
 * diagram typography, and removing it would silently degrade every diagram.
 */
export function sanitizeFragment(fragmentHtml: string): string {
    DOMPurify.addHook('uponSanitizeAttribute', (_node, data) => {
        if ((data.attrName === 'style' || URL_BEARING_SVG_ATTRIBUTES.includes(data.attrName)) && !isCssValueSafe(data.attrValue)) {
            data.keepAttr = false;
        }
    });
    // `<style>` is emptied rather than forbidden, exactly as `SvgSanitizer` does it: PlantUML puts diagram CSS
    // in a style element, so dropping the element would cost every diagram its appearance, while dropping only
    // an unsafe rule set costs nothing that should have been there.
    DOMPurify.addHook('uponSanitizeElement', (node, data) => {
        if (data.tagName === 'style' && !isCssValueSafe(node.textContent)) {
            node.textContent = '';
        }
    });
    try {
        return DOMPurify.sanitize(fragmentHtml, { FORBID_TAGS: DENIED_ELEMENTS });
    } finally {
        DOMPurify.removeHook('uponSanitizeAttribute');
        DOMPurify.removeHook('uponSanitizeElement');
    }
}

/**
 * Removes the attributes that describe the viewer rather than the statement: the container's `data-feedback`
 * (the student's own test names, messages and credits), every task's `data-feedback` id list, and the
 * container's `data-result` (score, points, commit hash, submission date, assessment type).
 *
 * None of it is needed here: this client renders with `includeJs: false` and opens the Angular feedback dialog
 * from the `Result` it already holds. It is stripped rather than left in place because the frame is where a
 * bypass would be able to observe it, and observable data is the only thing worth exfiltrating.
 *
 * The server keeps emitting all of it; the standalone consumer's `interactive.js` reads it.
 */
export function stripSensitiveAttributes(root: Element): void {
    for (const attribute of SENSITIVE_ATTRIBUTES) {
        if (root.hasAttribute(attribute)) {
            root.removeAttribute(attribute);
        }
        root.querySelectorAll(`[${attribute}]`).forEach((element) => element.removeAttribute(attribute));
    }
}

/**
 * Replaces every image the frame could only fetch with credentials by its alt text.
 *
 * Artemis-hosted markdown images are authenticated (`/api/core/files/**` is not `permitAll`). Inside the frame
 * the request is anonymous, so the image would simply fail to load in Chromium and Firefox. The client asks the
 * server for `inlineImages: true`, which turns local files into `data:` URIs, but that pass leaves the original
 * URL in place whenever the file is missing, oversized or past its limits, which is the case handled here.
 *
 * The replacement is the alt text rather than a blank placeholder: a placeholder would silently hide a diagram
 * the instructor deliberately put there. A decorative image (`alt=""`) is removed, and one without any alt text
 * falls back to the caller's localized string.
 *
 * `srcset`, `<picture><source>` and SVG `href` cannot reach this point through the current server sanitization
 * (`Safelist.relaxed()` permits neither `srcset` nor `picture`/`source`, and `SvgSanitizer` denies the `image`
 * and `use` elements), and are covered anyway, because this layer exists for the case where it was bypassed.
 */
export function rewriteSameOriginImages(root: Element, applicationOrigin: string, assetOrigin: string, unavailableLabel: string): void {
    // Both origins count as "ours": the application the reader is on, and wherever the server said its own assets
    // live. They are usually the same host and need not be.
    const ourOrigins = [...new Set([applicationOrigin, assetOrigin])];
    const isSameOrigin = (value: string): boolean => {
        try {
            return ourOrigins.includes(new URL(value, applicationOrigin).origin);
        } catch {
            // An unparseable reference cannot be shown to be safe, so it is treated as one to replace.
            return true;
        }
    };

    // `srcset` is a second fetch path DOMPurify keeps by default, and neither the server safelist nor this
    // client has any use for it. The SVG `image` and `use` elements are already gone, forbidden in
    // `sanitizeFragment`.
    root.querySelectorAll('source[srcset], img[srcset]').forEach((element) => element.removeAttribute('srcset'));

    // The backstop. `img[src]` gets the alt-text treatment below because it is the one the reader was meant to
    // see; every other same-origin reference is simply dropped, since it can only ever fail to load in here.
    // Anchors are exempt: a link is not a fetch, and the frame hands its clicks to the parent instead.
    root.querySelectorAll('*').forEach((element) => {
        if (element.tagName === 'A' || element.tagName === 'IMG') {
            return;
        }
        for (const attribute of URL_BEARING_ATTRIBUTES) {
            const value = element.getAttribute(attribute);
            if (value && !value.startsWith('#') && !value.startsWith('data:') && isSameOrigin(value)) {
                element.removeAttribute(attribute);
            }
        }
    });

    root.querySelectorAll('img').forEach((image) => {
        const source = image.getAttribute('src') ?? '';
        if (source.startsWith('data:') || !isSameOrigin(source)) {
            return;
        }
        const alt = image.getAttribute('alt');
        if (alt === '') {
            // An explicitly empty alt marks the image as decorative, so nothing takes its place.
            image.remove();
            return;
        }
        const fallback = image.ownerDocument.createElement('span');
        fallback.className = 'artemis-image-unavailable';
        fallback.textContent = alt || unavailableLabel;
        image.replaceWith(fallback);
    });
}

/**
 * Renders the inert `<span class="katex-formula" data-formula data-display-mode>` placeholders the server emits.
 *
 * Uses the public `renderToString`, which builds the same tree `katex.render` appends (`render` is
 * `node.appendChild(renderToDomTree(expr, options).toNode())`, `renderToString` is the same builder via
 * `toMarkup()`); the corpus specs pin that equivalence rather than assume it.
 */
export function renderFormulas(root: Element): void {
    root.querySelectorAll<HTMLElement>('.katex-formula').forEach((element) => {
        const formula = element.getAttribute('data-formula') ?? '';
        try {
            // nosemgrep -- KaTeX output over an inert DOMParser document; see the note below
            element.innerHTML = katex.renderToString(formula, {
                displayMode: element.getAttribute('data-display-mode') === 'true',
                throwOnError: false,
                output: 'html',
            });
        } catch {
            element.textContent = formula;
        }
    });
}

/**
 * Syntax-highlights the server's code blocks with highlight.js.
 *
 * The branches mirror the legacy markdown pipeline exactly ({@link file://../../../../foundation/util/markdown.conversion.util.ts},
 * `highlightWithHljs` / `addHljsClass`): an explicit known language is highlighted as that language, an explicit
 * unknown language keeps the escaped source, a block without a language is auto-detected, and every code block
 * gets the `hljs` class in all three cases. `hljs.highlightElement()` is deliberately not used: it auto-detects
 * for an unknown language, which is precisely where the legacy pipeline falls back to plain text.
 */
export function highlightCodeBlocks(root: Element): void {
    root.querySelectorAll<HTMLElement>(`pre code:not([${HIGHLIGHTED_MARKER}])`).forEach((element) => {
        element.setAttribute(HIGHLIGHTED_MARKER, 'true');
        // The palette keys off this class, and the legacy pipeline sets it on every code block, highlighted or not.
        element.classList.add('hljs');
        const code = element.textContent ?? '';
        const languageClass = [...element.classList].find((name) => name.startsWith(LANGUAGE_CLASS_PREFIX));
        const language = languageClass?.slice(LANGUAGE_CLASS_PREFIX.length) || undefined;
        // Per block: a grammar that throws must cost that block its colours and nothing else.
        try {
            // `code` comes from `textContent`, so it is text rather than markup, and highlight.js escapes it
            // again on the way out; its output is `<span class="hljs-*">` around escaped source and nothing else.
            if (!language) {
                // nosemgrep -- highlight.js output over text read from textContent, on an inert document
                element.innerHTML = hljs.highlightAuto(code).value;
            } else if (hljs.getLanguage(language)) {
                // nosemgrep -- highlight.js output over text read from textContent, on an inert document
                element.innerHTML = hljs.highlight(code, { language, ignoreIllegals: true }).value;
            }
            // Unknown language: the legacy pipeline emits the escaped source, which is what the server already
            // put into this element, so its content is left untouched.
        } catch {
            element.textContent = code;
        }
    });
}

/** Narrows the server's `data-test-status` to the known vocabulary; anything else degrades to "no result". */
function parseStatus(value: string | null): SsrTaskStatus {
    return SSR_TASK_STATUSES.find((status) => status === value) ?? 'no-result';
}

/** Reads the task metadata off the fragment. Must run before the sensitive attributes are stripped. */
function extractTasks(fragment: Element): SsrTask[] {
    return [...fragment.querySelectorAll('.artemis-task')].map((element, index) => ({
        index,
        taskName: element.getAttribute('data-task-name') ?? '',
        testIds: (element.getAttribute('data-test-ids') ?? '')
            .split(',')
            .filter((value) => value.length > 0)
            .map((value) => Number(value)),
        status: parseStatus(element.getAttribute('data-test-status')),
        authoredCount: Number(element.getAttribute('data-authored-count') ?? '0'),
        notExecutedCount: Number(element.getAttribute('data-not-executed-count') ?? '0'),
    }));
}

/**
 * Builds the sandboxed frame document from the server's rendered document.
 *
 * @param serverDocument the full document returned by the render endpoint
 * @param locale         the document language, for the `lang` attribute
 * @param unavailableLabel localized fallback for an image that cannot be shown and carries no alt text
 */
export function assembleFrameDocument(serverDocument: string, locale: string, unavailableLabel: string): AssembledFrame {
    const parsed = new DOMParser().parseFromString(serverDocument, 'text/html');
    // The endpoint still appends KaTeX scripts even with includeJs=false, and none of the server's scripts are
    // wanted here: KaTeX has already run in this file, and the trusted frame script is added below.
    parsed.querySelectorAll('script').forEach((script) => script.remove());

    const styleNodes = [...parsed.querySelectorAll('style, link[rel="stylesheet"]')];
    const fragment = parsed.querySelector('.artemis-problem-statement');
    if (!fragment) {
        return { srcdoc: '', generation: '', tasks: [], linkTargets: [] };
    }

    // Read before stripping: the task metadata lives in the same attributes some of which are about to go.
    const tasks = extractTasks(fragment);

    // The stylesheets are classpath resources plus the configured server URL, none of it derived from the
    // statement, and DOMPurify would strip the KaTeX <link> because no sanitizer allows a stylesheet reference
    // by default. Only the fragment goes through sanitization.
    const sanitized = new DOMParser().parseFromString(sanitizeFragment(fragment.outerHTML), 'text/html');
    const sanitizedFragment = sanitized.querySelector('.artemis-problem-statement');
    if (!sanitizedFragment) {
        return { srcdoc: '', generation: '', tasks: [], linkTargets: [] };
    }

    // Two different origins, deliberately not the same value. The asset origin is wherever the server said its
    // stylesheet and fonts live (`artemis.server-url`, which need not be the origin the client is served from), and
    // only the CSP needs it. Whether an image is "ours" is a question about the application's own origin: an
    // absolute URL back to the app would otherwise be judged cross-origin whenever the two differ, and would be
    // left in the frame to fail as an uncredentialed request.
    const katexLink = styleNodes.find((node) => node.tagName === 'LINK') as HTMLLinkElement | undefined;
    const assetOrigin = originOf(katexLink?.getAttribute('href')) ?? window.location.origin;
    const applicationOrigin = window.location.origin;

    stripSensitiveAttributes(sanitizedFragment);
    rewriteSameOriginImages(sanitizedFragment, applicationOrigin, assetOrigin, unavailableLabel);
    renderFormulas(sanitizedFragment);
    highlightCodeBlocks(sanitizedFragment);

    // Carried over from the server's own document rather than hardcoded: it is what selects the dark palette
    // (`body.artemis-ssr-body--dark` in dark-mode.css), and a frame that lost it would render a white page
    // inside a dark application.
    const bodyClass = parsed.body?.getAttribute('class') || 'artemis-ssr-body';

    const nonce = randomToken();
    const generation = randomToken();
    const script = FRAME_SCRIPT.replace(GENERATION_PLACEHOLDER, generation);
    const styles = styleNodes.map((node) => node.outerHTML).join('');

    // The policy is the first element in the head, before any stylesheet or script, because a policy only
    // governs what the parser sees after it.
    const srcdoc =
        `<!DOCTYPE html><html lang="${escapeAttribute(locale)}"><head><meta charset="UTF-8">` +
        `<meta http-equiv="Content-Security-Policy" content="${escapeAttribute(contentSecurityPolicy(nonce, assetOrigin))}">` +
        `<meta name="viewport" content="width=device-width, initial-scale=1.0">` +
        `${styles}</head><body class="${escapeAttribute(bodyClass)}">${sanitizedFragment.outerHTML}` +
        `<script nonce="${nonce}">${script}</script></body></html>`;

    const linkTargets = [...new Set([...sanitizedFragment.querySelectorAll('a[href]')].map((anchor) => anchor.getAttribute('href') ?? ''))];

    return { srcdoc, generation, tasks, linkTargets };
}

/**
 * Resolves a link the frame reported into a URL the parent may open, or `undefined` to ignore the request.
 *
 * Two independent conditions, both required. The scheme must be one a document may reasonably link to, which
 * excludes `javascript:`, `data:` and `blob:`. And the href must be one that is actually present in the
 * rendered statement, which is what stops the bridge from being a general "open any URL" capability: a message
 * the frame did not derive from a real anchor names a target that is not in the list.
 */
export function resolveFrameLink(href: string, linkTargets: readonly string[], origin: string): string | undefined {
    if (!linkTargets.includes(href)) {
        return undefined;
    }
    let url: URL;
    try {
        url = new URL(href, origin);
    } catch {
        return undefined;
    }
    if (!['http:', 'https:', 'mailto:'].includes(url.protocol)) {
        return undefined;
    }
    return url.href;
}

/** The origin of an absolute URL, or undefined when it is relative or unparseable. */
function originOf(href: string | null | undefined): string | undefined {
    if (!href) {
        return undefined;
    }
    try {
        return new URL(href, window.location.origin).origin;
    } catch {
        return undefined;
    }
}

/** Escapes a value for an HTML attribute delimited by double quotes. */
function escapeAttribute(value: string): string {
    return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
