import DOMPurify from 'dompurify';
import hljs from 'app/foundation/util/highlight-languages.util';
import { SSR_TASK_STATUSES, SsrTask, SsrTaskStatus } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

/**
 * Turns the server's rendered document into the single HTML string that is injected into the statement's shadow root.
 *
 * There is no origin boundary any more: the statement shares this document's origin, so the sanitization below is
 * the whole defense, not defense in depth. The server has already run its jsoup safelist; this pass runs DOMPurify
 * over the fragment again (it covers the PlantUML SVG the server safelist deliberately does not see) and then a
 * series of safe producers before the markup reaches the shadow root.
 *
 * The order below is fixed and pinned by specs, because each step assumes the previous one:
 *
 *   extract fragment -> DOMPurify -> read tasks -> strip sensitive attributes -> harden MathML
 *   -> rewrite same-origin images -> highlight.js -> serialize
 *
 * Formulas are server-generated native MathML; the client only hardens it (see `hardenMathml`), it does not produce
 * it. highlight.js runs here as a pure string producer (`highlight().value`), on an inert `DOMParser` document
 * attached to nothing: no script in it runs and no resource in it is fetched, and the markup leaves only as the
 * string injected into the shadow root. Its value is highlight.js' output over text read back from `textContent`,
 * which escapes what it emits.
 */

/** The `data-*` attributes the server writes that carry information about the viewer rather than the statement. */
const SENSITIVE_ATTRIBUTES = ['data-feedback', 'data-result'];

/**
 * SVG presentation attributes that accept a `url(...)` reference, mirroring `SvgSanitizer.URL_BEARING_ATTRIBUTES`.
 * An external reference in them issues a real network request: `fill`, `stroke`, `clip-path` and `mask` fetch in
 * Chromium, and `mask` fetches in Firefox and WebKit as well.
 */
const URL_BEARING_SVG_ATTRIBUTES = ['fill', 'stroke', 'filter', 'clip-path', 'mask', 'marker-start', 'marker-mid', 'marker-end'];

/**
 * Elements that may not appear in the statement.
 *
 * The first group is what `SvgSanitizer` denies outright, forbidden here again in case the server safelist is
 * bypassed. `feimage` joins them for the same reason it is guarded there: an `feImage` inside a filter fetches its
 * `href` in all three engines, which is not obvious from reading the element.
 *
 * The second group cannot be produced by the renderer at all: jsoup's `Safelist.relaxed()` permits none of them.
 * They are listed because each one is a fetch or a submission that the image rewriting below would not catch, and
 * forbidding an element the server can never emit costs nothing.
 *
 * `template` is the one element here that no later pass can cover. Its children live in a separate document
 * fragment, so neither `querySelectorAll('*')` nor `querySelectorAll('img')` in `rewriteSameOriginImages` reaches
 * them: a `<template><img src="https://…"></template>` walks through the URL rewriting untouched in all three
 * engines, and Firefox goes on to fetch the image. Denying the element is the only way to close that. It also keeps
 * a task inside a template from being counted by `extractTasks` but never appearing in the injected DOM.
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
    'template',
];

/**
 * Attributes that make the browser fetch or submit to their value.
 *
 * Enumerated because `img[src]` is not the only one: `<video poster>`, `<input type=image src>`, `<form action>`
 * and SVG `href` all issue a request. This pass is the backstop for anything the element list above lets through.
 */
const URL_BEARING_ATTRIBUTES = ['src', 'srcset', 'href', 'xlink:href', 'poster', 'data', 'action', 'formaction'];

/**
 * Path prefix of the server's file API. A local markdown image is rewritten to `${server.url}${SERVER_FILE_API_PATH}…`
 * (server-side `MARKDOWN_FILE_API_PATH`); `rewriteSameOriginImages` recognises such a leftover by this path.
 */
const SERVER_FILE_API_PATH = '/api/core/files/';

/** Matches a CSS comment, non-greedy across lines, as `SvgSanitizer.CSS_COMMENT_PATTERN` does. */
const CSS_COMMENT = /\/\*[\s\S]*?\*\//g;

/** Constructs that must never appear in a style value, mirroring `SvgSanitizer.DANGEROUS_CSS_PATTERN`. */
const DANGEROUS_CSS = /expression\s*\(|@import|-moz-binding/i;

/**
 * CSS functions that fetch an image without ever writing `url(`.
 *
 * `image-set("https://…" 1x)` takes a bare string as its source and is fetched in all three engines, so a rule
 * that only scans for `url(` misses it. These are rejected by name instead. None of them appears in PlantUML
 * output or in any legitimate statement CSS, so rejecting the whole declaration costs nothing.
 */
const IMAGE_FUNCTIONS = /(^|[^a-z-])(-webkit-)?(image-set|cross-fade|image|element|paint)\s*\(/i;

const URL_REFERENCE = /url\s*\(/gi;

const MATHML_NAMESPACE = 'http://www.w3.org/1998/Math/MathML';

/**
 * Presentation-MathML elements the client keeps, mirroring the server allowlist in `LatexToMathmlConverter`. Kept in
 * sync with it so the two boundaries agree on what valid formula output looks like.
 */
const MATHML_ELEMENTS = new Set([
    'math',
    'mrow',
    'mi',
    'mo',
    'mn',
    'ms',
    'mtext',
    'mspace',
    'msup',
    'msub',
    'msubsup',
    'mfrac',
    'msqrt',
    'mroot',
    'mstyle',
    'mpadded',
    'mphantom',
    'mfenced',
    'mtable',
    'mtr',
    'mtd',
    'munder',
    'mover',
    'munderover',
    'merror',
]);

/** Safe presentation attributes on a MathML element; everything else (href, src, width, …) is dropped. */
const MATHML_ATTRIBUTES = new Set([
    'mathvariant',
    'display',
    'displaystyle',
    'scriptlevel',
    'dir',
    'mathsize',
    'mathcolor',
    'accent',
    'accentunder',
    'stretchy',
    'fence',
    'separator',
    'form',
    'largeop',
    'movablelimits',
    'symmetric',
    'columnalign',
    'rowalign',
    'columnspan',
    'rowspan',
    'open',
    'close',
    'notation',
]);

/** Marks a code block that has already been highlighted, so a second pass over retained markup is a no-op. */
const HIGHLIGHTED_MARKER = 'data-highlighted';

/** The class CommonMark puts on a fenced code block, and the only place the authored language survives. */
const LANGUAGE_CLASS_PREFIX = 'language-';

/** The renderable statement, ready to be injected into a shadow root. */
export interface ShadowContent {
    /**
     * The server's document-level stylesheets, a wrapper carrying the body class, and the sanitized statement
     * fragment, concatenated as one string for the shadow root's `innerHTML`.
     */
    html: string;
    /** The tasks of this document, in document order; index i belongs to the i-th `.artemis-task` element. */
    tasks: SsrTask[];
    /**
     * Every `href` that survived sanitization into the statement.
     *
     * The content component honours a click on an anchor only for a link that is actually in this list, which is
     * what keeps a forged or unexpected anchor from becoming an open navigation primitive: the worst a click can
     * do is what the reader could have done by clicking a link that is really there.
     */
    linkTargets: string[];
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
 * from the `Result` it already holds. Stripping it keeps it out of reach of a sanitizer bypass: without an origin
 * boundary the statement shares the page's credentials, so this data must not sit in a DOM a bypass could read.
 * The server keeps emitting all of it, because the standalone consumer's `interactive.js` reads it.
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
 * Replaces every same-origin image the server did not inline by its alt text, and keeps external images from
 * leaking the page's referrer.
 *
 * The client asks the server for `inlineImages: true`, which turns Artemis-hosted markdown images into `data:`
 * URIs, but that pass leaves the original `/api/core/files/**` URL in place whenever the file is missing,
 * oversized or past its count limit. That leftover URL is what is handled here: it is dropped in favour of the
 * alt text so the statement never shows a broken-image icon (the file is missing or was rejected) and never
 * issues a second authenticated request for content that was meant to arrive inlined. This is consistent with the
 * behaviour when the statement still rendered inside a credential-less sandboxed frame, where such a request could
 * not have succeeded at all.
 *
 * The replacement is the alt text rather than a blank placeholder: a placeholder would silently hide a diagram
 * the instructor deliberately put there. A decorative image (`alt=""`) is removed, and one without any alt text
 * falls back to the caller's localized string.
 *
 * A retained external image keeps its `src` but gets `referrerpolicy="no-referrer"`, which the sandboxed iframe
 * used to provide for the whole document: without it the image request would announce which statement was viewed.
 *
 * `srcset`, `<picture><source>` and SVG `href` cannot reach this point through the current server sanitization
 * (`Safelist.relaxed()` permits neither `srcset` nor `picture`/`source`, and `SvgSanitizer` denies the `image`
 * and `use` elements), and are covered anyway, because this layer exists for the case where it was bypassed.
 */
export function rewriteSameOriginImages(root: Element, applicationOrigin: string, unavailableLabel: string): void {
    const isSameOrigin = (value: string): boolean => {
        try {
            const url = new URL(value, applicationOrigin);
            // "Ours" means either the application the reader is on, or a server file-API reference. The latter is
            // matched by path, not by origin: the server rewrites a local markdown image to an absolute
            // `${server.url}${SERVER_FILE_API_PATH}…` (MarkdownRelativeToAbsolutePathAttributeProvider), and
            // `server.url` need not be the origin the client is served from. Keying off the path catches that
            // leftover whether it is relative or absolute, without needing to know the server origin at all.
            return url.origin === applicationOrigin || url.pathname.startsWith(SERVER_FILE_API_PATH);
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
    // see; every other same-origin reference is simply dropped, since it can only ever fail to load. Anchors are
    // exempt: a link is not a fetch, and the content component resolves its clicks against `linkTargets`. Matched by
    // `localName`, not `tagName`, so an SVG `<a>` (whose `tagName` is lowercase `a`) keeps its href like an HTML one.
    root.querySelectorAll('*').forEach((element) => {
        if (element.localName === 'a' || element.localName === 'img') {
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
        if (source.startsWith('data:')) {
            return;
        }
        if (!isSameOrigin(source)) {
            // Retained external image. The sandboxed frame carried `referrerpolicy="no-referrer"` for the whole
            // document; without an iframe it has to be set per image so the fetch does not announce the viewer.
            image.setAttribute('referrerpolicy', 'no-referrer');
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
 * Enforces the server's Presentation-MathML allowlist a second time, as defense in depth.
 *
 * The formulas are server-generated MathML (see `LatexToMathmlConverter`), already sanitized against the same
 * allowlist before injection, so in practice this pass changes nothing. It exists because the render endpoint can be
 * served directly with `includeJs=true`, in which case this DOMPurify path is the only client-side sanitizer, and
 * because DOMPurify keeps MathML `href`/`src` and does not restrict the MathML element set to a presentation-only one.
 *
 * Three rules, all namespace-aware:
 * - a descendant of a `<math>` that is not itself in the MathML namespace is removed (the `<mtext><img>` /
 *   `<mtext><a>` integration-point escape: such an `<a>` would otherwise be collected into `linkTargets` and opened);
 * - a MathML-namespace element not on {@link MATHML_ELEMENTS} is removed;
 * - every attribute not on {@link MATHML_ATTRIBUTES} is dropped from a MathML element (this removes `href`, `src`, …).
 */
export function hardenMathml(root: Element): void {
    root.querySelectorAll('math').forEach((math) => {
        const walk = (element: Element): void => {
            // Depth first, over a snapshot: the checks below may remove the element, so its children are visited first.
            [...element.children].forEach(walk);
            if (element.namespaceURI !== MATHML_NAMESPACE) {
                element.remove();
                return;
            }
            if (element !== math && !MATHML_ELEMENTS.has(element.localName)) {
                element.remove();
                return;
            }
            [...element.attributes].forEach((attribute) => {
                if (!MATHML_ATTRIBUTES.has(attribute.localName ?? attribute.name)) {
                    element.removeAttribute(attribute.name);
                }
            });
        };
        walk(math);
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

/**
 * Reads the task metadata off the fragment.
 *
 * Run against the sanitized fragment, not the raw one: a task DOMPurify removed (for example inside a forbidden
 * `<template>`) must not be counted, or the index of every following task would drift out of step with the
 * `.artemis-task` elements that actually reach the shadow root.
 */
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
 * Builds the renderable statement from the server's rendered document.
 *
 * @param serverDocument   the full document returned by the render endpoint
 * @param unavailableLabel localized fallback for an image that cannot be shown and carries no alt text
 */
export function assembleShadowContent(serverDocument: string, unavailableLabel: string): ShadowContent {
    const parsed = new DOMParser().parseFromString(serverDocument, 'text/html');
    // The statement carries no script: `includeJs: false` already keeps the server from emitting any, and this
    // removes one that arrived anyway before it could reach the shadow root.
    parsed.querySelectorAll('script').forEach((script) => script.remove());

    const fragment = parsed.querySelector('.artemis-problem-statement');
    if (!fragment) {
        return { html: '', tasks: [], linkTargets: [] };
    }

    // Document-level stylesheets only, which is what the filter is for. A `<style>` inside the statement is not
    // hypothetical: PlantUML puts its diagram CSS in one, and an SVG `<style>` matches this selector like any
    // other. That copy is sanitized as part of the fragment and travels with it, so hoisting the pre-sanitization
    // original out here would hand the statement its `@import` and `url()` back unchecked and defeat the emptying
    // rule in `sanitizeFragment`.
    const styleNodes = [...parsed.querySelectorAll('style, link[rel="stylesheet"]')].filter((node) => !fragment.contains(node));

    // The stylesheets are the server's own classpath resources, none of it derived from the statement, so they are
    // kept as-is; only the fragment goes through sanitization.
    const sanitized = new DOMParser().parseFromString(sanitizeFragment(fragment.outerHTML), 'text/html');
    const sanitizedFragment = sanitized.querySelector('.artemis-problem-statement');
    if (!sanitizedFragment) {
        return { html: '', tasks: [], linkTargets: [] };
    }

    // Read after sanitization so a task DOMPurify removed cannot drift the index against the injected DOM.
    const tasks = extractTasks(sanitizedFragment);

    stripSensitiveAttributes(sanitizedFragment);
    // Before linkTargets is built, so a link smuggled through a MathML integration point cannot be collected.
    hardenMathml(sanitizedFragment);
    rewriteSameOriginImages(sanitizedFragment, window.location.origin, unavailableLabel);
    highlightCodeBlocks(sanitizedFragment);

    // The dark palette keys off this class. `body.artemis-ssr-body*` selectors do not match inside a shadow root,
    // so the server's body class goes on a wrapper element and the statement stylesheets match it as
    // `.artemis-ssr-body*` (see embedded.css / dark-mode.css). A statement that lost the class would render a
    // white page inside a dark application.
    const bodyClass = parsed.body?.getAttribute('class') || 'artemis-ssr-body';

    // The server generates these nodes from trusted CSS and configuration, so they are not sanitized like the
    // fragment. Event-handler attributes are stripped anyway as cheap defence: a `<link onload=...>` set through
    // innerHTML would otherwise fire, and nothing legitimate the server emits carries one.
    styleNodes.forEach((node) =>
        [...node.attributes].filter((attribute) => attribute.name.toLowerCase().startsWith('on')).forEach((attribute) => node.removeAttribute(attribute.name)),
    );
    const styles = styleNodes.map((node) => node.outerHTML).join('');
    // Built as a string for the shadow root's `innerHTML`. Every part is trusted by construction: `styles` are the
    // server's own document-level stylesheets, `bodyClass` is escaped, and the fragment is DOMPurify output run
    // through the safe producers above (strip, MathML harden, image rewrite, highlight), each of which emits escaped markup.
    // nosemgrep -- DOMPurify output plus the server's own stylesheets; see the note above
    const html = `${styles}<div class="${escapeAttribute(bodyClass)}">${sanitizedFragment.outerHTML}</div>`;

    // Both HTML `<a href>` and SVG `<a href>` / `<a xlink:href>` (PlantUML emits inline SVG, and SvgSanitizer keeps a
    // safe anchor URL), so the content component can validate a click on either against this list.
    const linkTargets = [
        ...new Set([...sanitizedFragment.querySelectorAll('a')].map((anchor) => anchor.getAttribute('href') ?? anchor.getAttribute('xlink:href') ?? '').filter((href) => href)),
    ];

    return { html, tasks, linkTargets };
}

/**
 * Resolves a clicked anchor's href into a URL the content component may open, or `undefined` to ignore it.
 *
 * Two independent conditions, both required. The scheme must be one a document may reasonably link to, which
 * excludes `javascript:`, `data:` and `blob:`. And the href must be one that is actually present in the
 * rendered statement, which is what stops a click from being a general "open any URL" capability: an href that
 * was not derived from a real anchor in the statement names a target that is not in the list.
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

/** Escapes a value for an HTML attribute delimited by double quotes. */
function escapeAttribute(value: string): string {
    return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
