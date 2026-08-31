import { assembleShadowContent, isCssValueSafe, resolveFrameLink, sanitizeFragment } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame.util';

/** Wraps a body in the document shape the render endpoint returns, including the stylesheet it prepends. */
const serverDocument = (body: string, options: { katex?: boolean; dark?: boolean } = {}): string =>
    `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"></head>` +
    `<body class="artemis-ssr-body${options.dark ? ' artemis-ssr-body--dark' : ''}">` +
    (options.katex ? '<link rel="stylesheet" href="http://localhost/assets/katex/katex.min.css">' : '') +
    '<style>.artemis-task{color:red}</style>' +
    `<div class="artemis-problem-statement">${body}</div></body></html>`;

const assemble = (body: string, options?: { katex?: boolean; dark?: boolean }) => assembleShadowContent(serverDocument(body, options), 'Image unavailable');

/** The assembled statement, parsed so its markup can be inspected the way a browser would see it once injected. */
const contentOf = (body: string, options?: { katex?: boolean; dark?: boolean }): Document => new DOMParser().parseFromString(assemble(body, options).html, 'text/html');

/** The statement fragment alone, for assertions about what the statement itself does or does not contain. */
const statementOf = (document_: Document): string => document_.querySelector('.artemis-problem-statement')?.outerHTML ?? '';

describe('problem statement shadow content assembly', () => {
    describe('what may reach the shadow root', () => {
        it("strips the feedback payload, which carries the student's own test results", () => {
            const body = '<span class="artemis-task" data-task-name="A" data-test-ids="1" data-feedback="1">A</span>';
            const server = serverDocument(body).replace(
                'class="artemis-problem-statement"',
                'class="artemis-problem-statement" data-feedback="{&quot;1&quot;:{&quot;name&quot;:&quot;testA&quot;}}"',
            );

            const content = new DOMParser().parseFromString(assembleShadowContent(server, 'Image unavailable').html, 'text/html');

            expect(content.querySelectorAll('[data-feedback]')).toHaveLength(0);
            expect(statementOf(content)).not.toContain('testA');
        });

        it('strips the result summary, which carries score, points and commit metadata', () => {
            const server = serverDocument('<p>x</p>').replace('class="artemis-problem-statement"', 'class="artemis-problem-statement" data-result="{&quot;score&quot;:42}"');

            const content = new DOMParser().parseFromString(assembleShadowContent(server, 'Image unavailable').html, 'text/html');

            expect(content.querySelectorAll('[data-result]')).toHaveLength(0);
            expect(statementOf(content)).not.toContain('42');
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

        it('reads tasks after sanitization, so a task inside a forbidden element does not drift the index', () => {
            // The template is denied by DOMPurify, so its task must not be counted: were it read before sanitization,
            // the index of the real task after it would be off by one against the markup that reaches the shadow root.
            const assembled = assemble('<template><span class="artemis-task" data-task-name="ghost">g</span></template><span class="artemis-task" data-task-name="real">r</span>');

            expect(assembled.tasks).toHaveLength(1);
            expect(assembled.tasks[0]).toMatchObject({ index: 0, taskName: 'real' });
        });
    });

    describe('images', () => {
        it('replaces an Artemis-hosted image the server could not inline by its alt text', () => {
            const content = contentOf('<img src="/api/core/files/markdown/diagram.png" alt="Class diagram">');

            expect(content.querySelector('img')).toBeNull();
            expect(content.querySelector('.artemis-image-unavailable')?.textContent).toBe('Class diagram');
        });

        it('replaces a server-hosted image the server rewrote to an absolute URL on a different origin, without a formula to reveal it', () => {
            // The server rewrites a local markdown image to `${server.url}/api/core/files/markdown/…`, and `server.url`
            // need not be the page origin. There is no formula here, so no KaTeX <link> carries the server origin: the
            // leftover has to be recognised by its file-API path, or it would be kept as an external image and issue a
            // second, credential-mismatched request instead of showing its alt text.
            const content = contentOf('<img src="https://artemis.example.com/api/core/files/markdown/diagram.png" alt="Class diagram">');

            expect(content.querySelector('img')).toBeNull();
            expect(content.querySelector('.artemis-image-unavailable')?.textContent).toBe('Class diagram');
        });

        it('removes an Artemis-hosted image that is marked decorative instead of announcing it', () => {
            const content = contentOf('<img src="/api/core/files/markdown/spacer.png" alt="">');

            expect(content.querySelector('img')).toBeNull();
            expect(content.querySelector('.artemis-image-unavailable')).toBeNull();
        });

        it('falls back to the localized label when a replaced image carries no alt text at all', () => {
            expect(contentOf('<img src="/api/core/files/markdown/x.png">').querySelector('.artemis-image-unavailable')?.textContent).toBe('Image unavailable');
        });

        it('keeps an inlined data URI, which is what the server sends once inlineImages is on', () => {
            expect(contentOf('<img src="data:image/png;base64,AAAA" alt="d">').querySelector('img')?.getAttribute('src')).toBe('data:image/png;base64,AAAA');
        });

        it('keeps a genuinely external image but stops it leaking the referrer', () => {
            const image = contentOf('<img src="https://img.example.org/badge.svg" alt="badge">').querySelector('img');

            expect(image).not.toBeNull();
            expect(image?.getAttribute('referrerpolicy')).toBe('no-referrer');
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
            // request in Chromium, Firefox and WebKit. The statement never needs them, so a same-origin reference is
            // dropped as the backstop for anything the element list lets through.
            expect(contentOf(markup).body.innerHTML).not.toContain('/api/core/files');
        });

        it('leaves an internal link alone, because a link is not a fetch', () => {
            // The content component resolves the click against `linkTargets`; the href itself stays in the markup.
            expect(contentOf('<a href="/courses/1">course</a>').querySelector('a')?.getAttribute('href')).toBe('/courses/1');
        });

        it('drops srcset, a second fetch path with no legitimate source here', () => {
            expect(contentOf('<img src="https://img.example.org/a.png" srcset="https://img.example.org/a2.png 2x" alt="a">').querySelector('img')?.hasAttribute('srcset')).toBe(
                false,
            );
        });

        it('denies a template, whose children no later pass can reach', () => {
            const content = contentOf('<template><img src="/api/core/files/markdown/x.png"><img src="https://img.example.org/a.png"></template>');

            expect(content.querySelector('template')).toBeNull();
            expect(statementOf(content)).not.toContain('/api/core/files');
            expect(statementOf(content)).not.toContain('img.example.org');
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
            { case: 'a quoted font family, which PlantUML emits', value: 'font-family:"Helvetica Neue",sans-serif' },
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

    describe('links', () => {
        const targets = ['https://example.org/docs', '/courses/1', 'mailto:tutor@example.org'];

        it('opens a link that is actually in the statement', () => {
            expect(resolveFrameLink('https://example.org/docs', targets, 'http://localhost')).toBe('https://example.org/docs');
        });

        it('ignores a link that is not in the statement, which is what an unexpected anchor looks like', () => {
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

    describe('the content it produces', () => {
        it('carries the dark body class onto the wrapper, or the statement would render light inside a dark application', () => {
            expect(contentOf('<p>x</p>', { dark: true }).querySelector('.artemis-ssr-body--dark')).not.toBeNull();
        });

        it('keeps the stylesheets the server ships with the statement', () => {
            const content = contentOf('<p>x</p>', { katex: true });

            expect(content.querySelector('style')?.textContent).toContain('.artemis-task');
            expect(content.querySelector('link[rel="stylesheet"]')?.getAttribute('href')).toContain('katex.min.css');
        });

        it('lists the links it contains, so the content component can tell a real one from an unexpected click', () => {
            expect(assemble('<a href="https://example.org">a</a><a href="https://example.org">again</a><a href="/x">b</a>').linkTargets).toEqual(['https://example.org', '/x']);
        });

        it('lists an SVG anchor too, so a click on a diagram link is validated like any other', () => {
            // PlantUML emits inline SVG; an SVG `<a>` is not an HTMLAnchorElement, so it is collected explicitly.
            expect(assemble('<svg><a href="https://example.org/svg">x</a></svg>').linkTargets).toContain('https://example.org/svg');
        });

        it('keeps a same-origin SVG anchor href, which the URL rewrite must not strip', () => {
            // An SVG `<a>` has the lowercase tag name `a`, so the URL-rewrite exemption has to match by `localName`
            // or the relative href would be dropped and the link would resolve against nothing.
            const assembled = assemble('<svg><a href="/courses/1"><text>x</text></a></svg>');

            expect(assembled.html).toContain('/courses/1');
            expect(assembled.linkTargets).toContain('/courses/1');
        });

        it('does not hoist a style element out of the statement, which would hand back the css the sanitizer emptied', () => {
            // PlantUML ships diagram CSS in an SVG `<style>`, so this element really does occur inside a statement.
            const assembled = assemble('<svg><style>.a{background:url(https://attacker.example/b.png)}@import url(https://attacker.example/x.css);</style></svg>');

            expect(assembled.html).not.toContain('attacker.example');
            expect(assembled.html).not.toContain('@import');
            // The server's own stylesheet still has to survive the filter, or the statement loses its typography.
            expect(assembled.html).toContain('.artemis-task');
        });

        it('produces nothing at all when the response carries no statement', () => {
            expect(assembleShadowContent('<html><body><p>no statement here</p></body></html>', 'x')).toEqual({ html: '', tasks: [], linkTargets: [] });
        });
    });

    describe('formulas and code, precomputed here rather than in the browser at display time', () => {
        it('renders a katex placeholder into real markup', () => {
            const content = contentOf('<span class="katex-formula" data-formula="x^2" data-display-mode="false"></span>');

            expect(content.querySelector('.katex-formula')?.innerHTML).toContain('katex');
        });

        it('passes the display mode through for a block formula', () => {
            const content = contentOf('<span class="katex-formula" data-formula="x^2" data-display-mode="true"></span>');

            expect(content.querySelector('.katex-formula')?.innerHTML).toContain('katex-display');
        });

        it('falls back to the source when a formula cannot be rendered', () => {
            const content = contentOf('<span class="katex-formula" data-formula="\\unknowncommand{" data-display-mode="false"></span>');

            // throwOnError is off, so KaTeX renders an error node rather than throwing; either way the reader is
            // never shown an empty placeholder.
            expect(content.querySelector('.katex-formula')?.textContent).not.toBe('');
        });

        it('caps the size a formula may ask for, which KaTeX itself leaves unbounded', () => {
            const content = contentOf('<span class="katex-formula" data-formula="\\rule{1000000000em}{1000000000em}" data-display-mode="false"></span>');
            const sizes = [...(content.querySelector('.katex-formula')?.querySelectorAll<HTMLElement>('[style*="em"]') ?? [])].flatMap((element) =>
                [...element.getAttribute('style')!.matchAll(/([\d.]+)em/g)].map((match) => Number(match[1])),
            );

            expect(sizes.length).toBeGreaterThan(0);
            expect(Math.max(...sizes)).toBeLessThanOrEqual(100);
        });

        it('highlights a code block whose language is registered', () => {
            const content = contentOf('<pre><code class="language-java">int x = 1;</code></pre>');
            const code = content.querySelector('pre code');

            expect(code?.className).toContain('hljs');
            expect(code?.innerHTML).toContain('<span class="hljs-type">int</span>');
        });

        it('leaves a block with an unregistered language escaped, but still marks it', () => {
            const content = contentOf('<pre><code class="language-notalanguage">int x = 1;</code></pre>');
            const code = content.querySelector('pre code');

            expect(code?.className).toContain('hljs');
            expect(code?.innerHTML).not.toContain('hljs-');
            expect(code?.textContent).toBe('int x = 1;');
        });

        it('auto-detects the language of a block that declares none', () => {
            expect(contentOf('<pre><code>public class A { }</code></pre>').querySelector('pre code')?.innerHTML).toContain('hljs-');
        });
    });
});
