import { assembleFrameDocument, isCssValueSafe, resolveFrameLink, sanitizeFragment } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame.util';

/** Wraps a body in the document shape the render endpoint returns, including the stylesheet it prepends. */
const serverDocument = (body: string, options: { katex?: boolean; dark?: boolean } = {}): string =>
    `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"></head>` +
    `<body class="artemis-ssr-body${options.dark ? ' artemis-ssr-body--dark' : ''}">` +
    (options.katex ? '<link rel="stylesheet" href="http://localhost/assets/katex/katex.min.css">' : '') +
    '<style>.artemis-task{color:red}</style>' +
    `<div class="artemis-problem-statement">${body}</div></body></html>`;

const assemble = (body: string, options?: { katex?: boolean; dark?: boolean }) => assembleFrameDocument(serverDocument(body, options), 'en', 'Image unavailable');

/** The assembled frame, parsed so its markup can be inspected the way a browser would see it. */
const frameOf = (body: string, options?: { katex?: boolean; dark?: boolean }): Document => new DOMParser().parseFromString(assemble(body, options).srcdoc, 'text/html');

/**
 * The statement markup alone. Assertions about what the statement does or does not contain have to use this
 * rather than the whole body: the body also holds the frame script, whose 32-character hex generation token
 * contains any given two-digit string often enough to make such an assertion fail at random.
 */
const statementOf = (document_: Document): string => document_.querySelector('.artemis-problem-statement')?.outerHTML ?? '';

const cspOf = (document_: Document): string => document_.querySelector('meta[http-equiv="Content-Security-Policy"]')?.getAttribute('content') ?? '';

describe('problem statement frame assembly', () => {
    describe('content security policy', () => {
        it('is the first element in the head, because a policy only governs what the parser sees after it', () => {
            const head = frameOf('<p>x</p>').head;
            const elements = [...head.children];

            // charset has to come first for the parser; the policy is the first thing after it and, crucially,
            // before any stylesheet or script.
            expect(elements[0].getAttribute('charset')).toBe('UTF-8');
            expect(elements[1].getAttribute('http-equiv')).toBe('Content-Security-Policy');
            expect(head.querySelector('style, link')).not.toBe(elements[1]);
        });

        it('denies everything by default and opens only what the statement genuinely needs', () => {
            const csp = cspOf(frameOf('<p>x</p>'));

            expect(csp).toContain("default-src 'none'");
            // The three that make a smuggled payload useless even if it were allowed to run.
            expect(csp).toContain("connect-src 'none'");
            expect(csp).toContain("form-action 'none'");
            expect(csp).toContain("object-src 'none'");
            expect(csp).toContain("base-uri 'none'");
            // Never: it would let an injected script execute the moment the sandbox is the only barrier left.
            expect(csp).not.toContain('unsafe-eval');
            expect(csp).not.toContain("script-src 'unsafe-inline'");
        });

        it('admits scripts only by nonce, and gives that nonce to exactly one element', () => {
            const document_ = frameOf('<p>x</p><script>window.__x = 1;</script>');
            const scripts = [...document_.querySelectorAll('script')];
            const nonce = scripts[0].getAttribute('nonce');

            expect(scripts).toHaveLength(1);
            expect(nonce).toMatch(/^[0-9a-f]{32}$/);
            expect(cspOf(document_)).toContain(`script-src 'nonce-${nonce}'`);
        });

        it('gives every frame its own nonce and generation, so neither can be predicted from an earlier render', () => {
            const first = assemble('<p>x</p>');
            const second = assemble('<p>x</p>');

            expect(first.generation).toMatch(/^[0-9a-f]{32}$/);
            expect(first.generation).not.toBe(second.generation);
            expect(first.srcdoc).not.toBe(second.srcdoc);
        });

        it('allows the stylesheet and fonts from the server that shipped them', () => {
            const csp = cspOf(frameOf('<p>x</p>', { katex: true }));

            expect(csp).toContain('font-src http://localhost');
            expect(csp).toContain("style-src 'unsafe-inline' http://localhost");
        });
    });

    describe('what the frame may see', () => {
        it("strips the feedback payload, which carries the student's own test results", () => {
            const body = '<span class="artemis-task" data-task-name="A" data-test-ids="1" data-feedback="1">A</span>';
            const server = serverDocument(body).replace(
                'class="artemis-problem-statement"',
                'class="artemis-problem-statement" data-feedback="{&quot;1&quot;:{&quot;name&quot;:&quot;testA&quot;}}"',
            );

            const frame = new DOMParser().parseFromString(assembleFrameDocument(server, 'en', 'Image unavailable').srcdoc, 'text/html');

            expect(frame.querySelectorAll('[data-feedback]')).toHaveLength(0);
            expect(statementOf(frame)).not.toContain('testA');
        });

        it('strips the result summary, which carries score, points and commit metadata', () => {
            const server = serverDocument('<p>x</p>').replace('class="artemis-problem-statement"', 'class="artemis-problem-statement" data-result="{&quot;score&quot;:42}"');

            const frame = new DOMParser().parseFromString(assembleFrameDocument(server, 'en', 'Image unavailable').srcdoc, 'text/html');

            expect(frame.querySelectorAll('[data-result]')).toHaveLength(0);
            expect(statementOf(frame)).not.toContain('42');
        });

        it('keeps the task metadata the client itself needs', () => {
            const assembled = assemble(
                '<span class="artemis-task" data-task-name="A" data-test-ids="1,2" data-test-status="fail" data-authored-count="2" data-not-executed-count="1">A</span>',
            );

            expect(assembled.tasks).toEqual([{ index: 0, taskName: 'A', testIds: [1, 2], status: 'fail', authoredCount: 2, notExecutedCount: 1 }]);
        });

        it('degrades an unknown task status to "no result" rather than trusting it', () => {
            expect(assemble('<span class="artemis-task" data-test-status="something-new">A</span>').tasks[0].status).toBe('no-result');
        });
    });

    describe('images', () => {
        it('replaces an Artemis-hosted image by its alt text, because the frame cannot authenticate for it', () => {
            const frame = frameOf('<img src="/api/core/files/markdown/diagram.png" alt="Class diagram">');

            expect(frame.querySelector('img')).toBeNull();
            expect(frame.querySelector('.artemis-image-unavailable')?.textContent).toBe('Class diagram');
        });

        it('removes an Artemis-hosted image that is marked decorative instead of announcing it', () => {
            const frame = frameOf('<img src="/api/core/files/markdown/spacer.png" alt="">');

            expect(frame.querySelector('img')).toBeNull();
            expect(frame.querySelector('.artemis-image-unavailable')).toBeNull();
        });

        it('falls back to the localized label when a blocked image carries no alt text at all', () => {
            expect(frameOf('<img src="/api/core/files/markdown/x.png">').querySelector('.artemis-image-unavailable')?.textContent).toBe('Image unavailable');
        });

        it('keeps an inlined data URI, which is what the server sends once inlineImages is on', () => {
            expect(frameOf('<img src="data:image/png;base64,AAAA" alt="d">').querySelector('img')?.getAttribute('src')).toBe('data:image/png;base64,AAAA');
        });

        it('keeps a genuinely external image, which both existing renderers also show', () => {
            expect(frameOf('<img src="https://img.example.org/badge.svg" alt="badge">').querySelector('img')).not.toBeNull();
        });

        it.each([
            { case: 'an SVG filter image', markup: '<svg><filter id="f"><feImage href="/api/core/files/markdown/x.png"/></filter></svg>' },
            { case: 'a video poster', markup: '<video poster="/api/core/files/markdown/x.png"></video>' },
            { case: 'an image input', markup: '<input type="image" src="/api/core/files/markdown/x.png">' },
            { case: 'a form action', markup: '<form action="/api/core/files/markdown/x.png"></form>' },
            { case: 'an object', markup: '<object data="/api/core/files/markdown/x.png"></object>' },
            { case: 'an embed', markup: '<embed src="/api/core/files/markdown/x.png">' },
            { case: 'a nested frame', markup: '<iframe src="/api/core/files/markdown/x.png"></iframe>' },
            { case: 'a picture source', markup: '<picture><source srcset="/api/core/files/markdown/x.png"></picture>' },
            { case: 'an SVG pattern image', markup: '<svg><pattern id="p"><image href="/api/core/files/markdown/x.png"/></pattern></svg>' },
        ])('leaves no same-origin reference behind for $case', ({ markup }) => {
            // `img[src]` is not the only attribute a browser fetches, and each of these was observed issuing a real
            // request in Chromium, Firefox and WebKit. A request from the frame carries no credentials, so a
            // same-origin reference could never load anyway; what it could still do is announce that it was tried.
            expect(frameOf(markup).body.innerHTML).not.toContain('/api/core/files');
        });

        it('leaves an internal link alone, because a link is not a fetch', () => {
            // The frame cannot follow it either way: it reports the click and the parent decides.
            expect(frameOf('<a href="/courses/1">course</a>').querySelector('a')?.getAttribute('href')).toBe('/courses/1');
        });

        it('drops srcset, a second fetch path with no legitimate source here', () => {
            expect(frameOf('<img src="https://img.example.org/a.png" srcset="https://img.example.org/a2.png 2x" alt="a">').querySelector('img')?.hasAttribute('srcset')).toBe(
                false,
            );
        });
    });

    describe('url references, which are the exfiltration channel a CSS bypass would use', () => {
        it.each([
            { case: 'a plain external url', value: 'background-image:url(https://evil.example/x)' },
            { case: 'an uppercase URL token', value: 'background-image:URL(https://evil.example/x)' },
            { case: 'a comment-obfuscated token', value: 'background-image:u/**/rl(https://evil.example/x)' },
            { case: 'a backslash escape', value: 'background-image:u\\72l(https://evil.example/x)' },
            { case: 'a data url', value: 'background-image:url(data:image/gif;base64,AAAA)' },
            { case: 'a protocol-relative url', value: 'background-image:url(//evil.example/x)' },
            { case: 'a quoted external url', value: "background-image:url('https://evil.example/x')" },
            { case: 'an unterminated url', value: 'background-image:url(https://evil.example/x' },
            { case: '@import', value: '@import url(#local)' },
            { case: 'expression()', value: 'width:expression(alert(1))' },
            { case: '-moz-binding', value: '-moz-binding:url(#local)' },
            // image-set takes a bare string as its source, so there is no url( token to find. Verified fetching in
            // Chromium, Firefox and WebKit, which is why it is rejected by function name rather than by argument.
            { case: 'image-set with a bare string', value: 'background-image:image-set("https://evil.example/x" 1x)' },
            { case: 'the -webkit-prefixed image-set', value: 'background-image:-webkit-image-set("https://evil.example/x" 1x)' },
            { case: 'image-set in border-image', value: 'border-image-source:image-set("https://evil.example/x" 1x)' },
            { case: 'image-set in mask-image', value: 'mask-image:image-set("https://evil.example/x" 1x)' },
            { case: 'cross-fade', value: 'background-image:cross-fade(url(#a) 50%, url(#b) 50%)' },
            { case: 'the image() function', value: 'background-image:image("https://evil.example/x")' },
        ])('rejects $case', ({ value }) => {
            expect(isCssValueSafe(value)).toBe(false);
        });

        it.each([
            { case: 'a local fragment reference', value: 'fill:url(#gradient)' },
            { case: 'plain typography, which PlantUML emits', value: 'font-family:sans-serif;font-size:14px' },
            { case: 'nothing at all', value: '' },
            // PlantUML quotes font names; the rule must not reject a declaration merely for containing a string.
            { case: 'a quoted font family, which PlantUML emits', value: 'font-family:"Helvetica Neue",sans-serif' },
            // Nothing here is a function call, so the substring must not trip the rule.
            { case: 'a property whose name merely contains the word image', value: 'background-image:none' },
        ])('keeps $case', ({ value }) => {
            expect(isCssValueSafe(value)).toBe(true);
        });

        it('drops an external reference from a style attribute but keeps the diagram typography beside it', () => {
            const sanitized = sanitizeFragment('<div><span style="background-image:url(https://evil.example/x)">a</span><span style="font-size:14px">b</span></div>');

            expect(sanitized).not.toContain('evil.example');
            expect(sanitized).toContain('font-size:14px');
        });

        it.each(['fill', 'stroke', 'filter', 'clip-path', 'mask', 'marker-start', 'marker-mid', 'marker-end'])(
            'drops an external reference from the SVG %s attribute, which really does issue a request',
            (attribute) => {
                const sanitized = sanitizeFragment(`<svg><path ${attribute}="url(https://evil.example/x)"></path></svg>`);

                expect(sanitized).not.toContain('evil.example');
            },
        );

        it('keeps a local SVG paint reference, which is how a gradient is applied', () => {
            expect(sanitizeFragment('<svg><path fill="url(#grad)"></path></svg>')).toContain('url(#grad)');
        });

        it('empties a dangerous style element rather than removing it, so a diagram keeps its own rules', () => {
            const sanitized = sanitizeFragment('<svg><style>.a{background:url(https://evil.example/x)}</style><path></path></svg>');

            expect(sanitized).not.toContain('evil.example');
            expect(sanitized).toContain('<style>');
        });

        it.each(['script', 'foreignObject', 'use', 'image', 'animate', 'animateTransform', 'animateMotion', 'set', 'feImage'])('forbids the SVG %s element', (element) => {
            expect(sanitizeFragment(`<svg><${element}></${element}></svg>`).toLowerCase()).not.toContain(`<${element.toLowerCase()}`);
        });
    });

    describe('the link bridge', () => {
        const targets = ['https://example.org/docs', '/courses/1', 'mailto:tutor@example.org'];

        it('opens a link that is actually in the statement', () => {
            expect(resolveFrameLink('https://example.org/docs', targets, 'http://localhost')).toBe('https://example.org/docs');
        });

        it('ignores a link that is not in the statement, which is what a forged message looks like', () => {
            expect(resolveFrameLink('https://evil.example/steal', targets, 'http://localhost')).toBeUndefined();
        });

        it.each([
            { case: 'javascript', href: 'javascript:alert(1)' },
            { case: 'data', href: 'data:text/html,<script>alert(1)</script>' },
            { case: 'blob', href: 'blob:http://localhost/abc' },
        ])('refuses a $case url even when the statement contains it', ({ href }) => {
            expect(resolveFrameLink(href, [href], 'http://localhost')).toBeUndefined();
        });

        it('resolves an in-app link against the application origin', () => {
            expect(resolveFrameLink('/courses/1', targets, 'http://localhost')).toBe('http://localhost/courses/1');
        });
    });

    describe('the document it produces', () => {
        it('carries the dark body class over, or the frame would render a white page inside a dark application', () => {
            expect(frameOf('<p>x</p>', { dark: true }).body.className).toContain('artemis-ssr-body--dark');
        });

        it('keeps the stylesheets the server ships with the statement', () => {
            const frame = frameOf('<p>x</p>', { katex: true });

            expect(frame.querySelector('style')?.textContent).toContain('.artemis-task');
            expect(frame.querySelector('link[rel="stylesheet"]')?.getAttribute('href')).toContain('katex.min.css');
        });

        it('lists the links it contains, so the parent can tell a real one from a forged request', () => {
            expect(assemble('<a href="https://example.org">a</a><a href="https://example.org">again</a><a href="/x">b</a>').linkTargets).toEqual(['https://example.org', '/x']);
        });

        it('does not hoist a style element out of the statement, which would hand back the css the sanitizer emptied', () => {
            // PlantUML ships diagram CSS in an SVG `<style>`, so this element really does occur inside a statement.
            const frame = frameOf('<svg><style>.a{background:url(https://attacker.example/b.png)}@import url(https://attacker.example/x.css);</style></svg>');

            expect(frame.head.innerHTML).not.toContain('attacker.example');
            expect(frame.head.innerHTML).not.toContain('@import');
            // The server's own stylesheet still has to survive the filter, or the frame loses its typography.
            expect(frame.head.innerHTML).toContain('.artemis-task');
        });

        it('produces nothing at all when the response carries no statement', () => {
            expect(assembleFrameDocument('<html><body><p>no statement here</p></body></html>', 'en', 'x')).toEqual({ srcdoc: '', generation: '', tasks: [], linkTargets: [] });
        });
    });

    describe('formulas and code, which are precomputed here rather than inside the frame', () => {
        it('renders a katex placeholder into real markup', () => {
            const frame = frameOf('<span class="katex-formula" data-formula="x^2" data-display-mode="false"></span>');

            expect(frame.querySelector('.katex-formula')?.innerHTML).toContain('katex');
        });

        it('passes the display mode through for a block formula', () => {
            const frame = frameOf('<span class="katex-formula" data-formula="x^2" data-display-mode="true"></span>');

            expect(frame.querySelector('.katex-formula')?.innerHTML).toContain('katex-display');
        });

        it('falls back to the source when a formula cannot be rendered', () => {
            const frame = frameOf('<span class="katex-formula" data-formula="\\unknowncommand{" data-display-mode="false"></span>');

            // throwOnError is off, so KaTeX renders an error node rather than throwing; either way the reader is
            // never shown an empty placeholder.
            expect(frame.querySelector('.katex-formula')?.textContent).not.toBe('');
        });

        it('highlights a code block whose language is registered', () => {
            const frame = frameOf('<pre><code class="language-java">int x = 1;</code></pre>');
            const code = frame.querySelector('pre code');

            expect(code?.className).toContain('hljs');
            expect(code?.innerHTML).toContain('<span class="hljs-type">int</span>');
        });

        it('leaves a block with an unregistered language escaped, but still marks it', () => {
            const frame = frameOf('<pre><code class="language-notalanguage">int x = 1;</code></pre>');
            const code = frame.querySelector('pre code');

            expect(code?.className).toContain('hljs');
            expect(code?.innerHTML).not.toContain('hljs-');
            expect(code?.textContent).toBe('int x = 1;');
        });

        it('auto-detects the language of a block that declares none', () => {
            expect(frameOf('<pre><code>public class A { }</code></pre>').querySelector('pre code')?.innerHTML).toContain('hljs-');
        });
    });
});
