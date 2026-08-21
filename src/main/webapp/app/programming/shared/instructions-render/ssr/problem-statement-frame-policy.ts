/**
 * The security envelope of the sandboxed problem-statement frame: what the browser is told the frame may do.
 *
 * Deliberately its own module, free of any dependency. Everything else in this folder pulls in DOMPurify, KaTeX
 * and highlight.js, and these two values have to be importable from the Playwright specs, which run in Node and
 * assert the shipped policy rather than a copy of it. A copy is exactly what would rot.
 */

/**
 * The sandbox the frame is given.
 *
 * `allow-scripts` and nothing else. It is present only because one script has to run in there: the frame cannot
 * report its own height, resolve a click to a task, or hand a link to the parent without one, and that script is
 * the only element in the document that carries the CSP nonce below.
 *
 * Everything that is absent is a decision. `allow-same-origin` above all: adding it would give the statement
 * back the cookies, the storage and the parent DOM that this whole design exists to keep it away from, and it
 * would silently void every guarantee the browser tests assert.
 */
export const FRAME_SANDBOX = 'allow-scripts';

/**
 * The frame's own Content Security Policy.
 *
 * This is not defence in depth, it is load-bearing. A `srcdoc` document inherits the embedder's policy, and the
 * application policy carries `'unsafe-inline'` with no `default-src`, so on its own it would stop nothing. And
 * the sandbox alone is not enough everywhere: measured in all three engines, WebKit sends the `SameSite=Lax` JWT
 * cookie on requests issued from a sandboxed opaque-origin frame, where Chromium and Firefox send none. With
 * `csrf(CsrfConfigurer::disable)` on the server, that would be a blind CSRF channel on Safari.
 *
 * So each of these does real work: `connect-src 'none'` removes fetch, sendBeacon and WebSocket;
 * `default-src 'none'` removes workers; the absent `'unsafe-eval'` removes `eval`; and `script-src` by nonce
 * means an injected script cannot execute even though the sandbox permits scripts at all. All four were verified
 * to be reachable when this policy is taken away.
 *
 * `img-src` stays open to `https:` so that externally hosted images in a statement keep rendering, as they do in
 * both existing renderers. That is an accepted residual and not an oversight: it leaves an author able to learn
 * that a statement was viewed, which a plain markdown image already achieves today.
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
