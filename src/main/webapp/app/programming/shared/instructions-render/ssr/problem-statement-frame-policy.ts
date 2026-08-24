/**
 * The security envelope of the sandboxed problem-statement frame: what the browser is told the frame may do.
 *
 * Its own module, free of any dependency: everything else in this folder pulls in DOMPurify, KaTeX and
 * highlight.js, while these two values have to be importable from the Playwright specs, which run in Node and
 * assert the shipped policy rather than a copy of it.
 */

/**
 * The sandbox the frame is given.
 *
 * `allow-scripts` and nothing else. It is present because one script has to run in there: without it the frame
 * cannot report its own height, resolve a click to a task, or hand a link to the parent. That script is the only
 * element in the document carrying the CSP nonce below.
 *
 * `allow-same-origin` is absent above all: it would give the statement back the cookies, the storage and the
 * parent DOM that the opaque origin exists to keep it away from.
 */
export const FRAME_SANDBOX = 'allow-scripts';

/**
 * The frame's own Content Security Policy.
 *
 * Load-bearing rather than defence in depth. A `srcdoc` document inherits the embedder's policy, and the
 * application policy carries `'unsafe-inline'` with no `default-src`, so on its own it would stop nothing. The
 * sandbox alone is not enough either: WebKit sends the `SameSite=Lax` JWT cookie on requests issued from a
 * sandboxed opaque-origin frame where Chromium and Firefox send none, which with `csrf(CsrfConfigurer::disable)`
 * on the server is a blind CSRF channel on Safari.
 *
 * Each directive does real work: `connect-src 'none'` removes fetch, sendBeacon and WebSocket; `default-src
 * 'none'` removes workers; the absent `'unsafe-eval'` removes `eval`; and `script-src` by nonce means an injected
 * script cannot execute even though the sandbox permits scripts at all.
 *
 * `img-src` stays open to `https:` so that externally hosted images in a statement keep rendering, as they do in
 * both existing renderers. This is an accepted residual: it leaves an author able to learn that a statement was
 * viewed, which a plain markdown image already achieves.
 *
 * @param nonce  the per-render nonce, 128 bits of hex, carried by the trusted frame script and nothing else
 * @param origin the server that shipped the statement's stylesheet and fonts
 */
export function contentSecurityPolicy(nonce: string, origin: string): string {
    return [
        "default-src 'none'",
        `script-src 'nonce-${nonce}'`,
        `style-src 'unsafe-inline' ${origin}`,
        `font-src ${origin}`,
        'img-src data: https:',
        "connect-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
        "frame-src 'none'",
        "object-src 'none'",
    ].join('; ');
}
