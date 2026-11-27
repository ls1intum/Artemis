import { describe, expect, it } from 'vitest';
import { markdownFileDestinationSuffix, markdownFileDestinations } from './markdown-file-destinations';

const P = '/api/core/files/markdown/A.png';

/** The text of every destination that is found, in the order of the document. */
function destinationTexts(source: string): string[] {
    return markdownFileDestinations(source).map(({ start, end }) => source.slice(start, end));
}

/** Replaces each destination with the text that `replacement` gives, as the importer does: from the end, so that earlier offsets stay valid. */
function replaceDestinations(source: string, replacement: (text: string) => string): string {
    let result = source;
    for (const { start, end } of [...markdownFileDestinations(source)].reverse()) {
        result = result.slice(0, start) + replacement(source.slice(start, end)) + result.slice(end);
    }
    return result;
}

describe('markdownFileDestinations', () => {
    it('matches the decoded URL path and returns only its query and fragment', () => {
        expect(markdownFileDestinationSuffix('/api/core/files/markdown/A.pdf#page=2', 'A.pdf')).toBe('#page=2');
        expect(markdownFileDestinationSuffix('/api/core/files/markdown/A%20B.pdf?download=1#page=2', 'A B.pdf')).toBe('?download=1#page=2');
        expect(markdownFileDestinationSuffix('https://example.org/?redirect=/api/core/files/markdown/A.pdf', 'A.pdf')).toBeUndefined();
        expect(markdownFileDestinationSuffix('/x/foomarkdown/A.pdf', 'A.pdf')).toBeUndefined();
    });

    it('locates only destinations, preserving labels, titles, quoting and CRLF source offsets', () => {
        const path = '/api/core/files/markdown/A.png';
        const source = `![${path}](<${path}> "${path}")\r\n[attachment][file]\r\n\r\n[file]: ${path} "${path}"\r\n\r\n<img SRC = '${path}' alt="${path}"> <a href=${path}>${path}</a>`;
        const destinations = markdownFileDestinations(source);

        expect(destinations).toHaveLength(4);
        for (const destination of destinations) {
            expect(destination.url).toBe(path);
            expect(source.slice(destination.start, destination.end)).toBe(path);
        }
        let updated = source;
        for (const destination of destinations.sort((a, b) => b.start - a.start)) {
            updated = updated.slice(0, destination.start) + '/new.png' + updated.slice(destination.end);
        }
        expect(updated).toBe(
            `![${path}](</new.png> "${path}")\r\n[attachment][file]\r\n\r\n[file]: /new.png "${path}"\r\n\r\n<img SRC = '/new.png' alt="${path}"> <a href=/new.png>${path}</a>`,
        );
    });

    it('finds destinations inside lists and blockquotes and angle-bracket autolinks', () => {
        const source = '> - ![image](/api/core/files/markdown/A.png)\n\n<https://source.example/api/core/files/markdown/B.png>';
        expect(markdownFileDestinations(source).map((destination) => source.slice(destination.start, destination.end))).toEqual([
            '/api/core/files/markdown/A.png',
            'https://source.example/api/core/files/markdown/B.png',
        ]);
    });

    it.each([
        '\\[label](/api/core/files/markdown/A.png)',
        '<!--\n![image](/api/core/files/markdown/A.png)\n<img src="/api/core/files/markdown/A.png">\n-->',
        '<script>const example = \'<img src="/api/core/files/markdown/A.png">\';</script>',
        '````markdown\n```text\n![image](/api/core/files/markdown/A.png)\n```\n````',
        '> ```text\n> ![image](/api/core/files/markdown/A.png)\n> ```',
        '<code>![image](/api/core/files/markdown/A.png) <img src="/api/core/files/markdown/A.png"></code>',
    ])('does not collect literal references in %s', (source) => {
        expect(markdownFileDestinations(source)).toEqual([]);
    });

    describe('links and images', () => {
        it.each<[string, string, string[]]>([
            ['inline link', `[x](${P})`, [P]],
            ['image', `![x](${P})`, [P]],
            ['angle brackets', `[x](<${P}>)`, [P]],
            ['angle brackets with a space', '[x](</a b.png>)', ['/a b.png']],
            ['title in double quotes', `[x](${P} "title")`, [P]],
            ['title in single quotes', `[x](${P} 'title')`, [P]],
            ['title in parentheses', `[x](${P} (title))`, [P]],
            ['title with parentheses', `[x](${P} "ti(t)le")`, [P]],
            ['white space around the destination', `[x](  ${P}  )`, [P]],
            ['destination on its own line', `[x](\n${P}\n  "title"\n)`, [P]],
            ['empty destination', '[x]() ![y]() [z](<>)', []],
            ['unterminated link', `[x](${P}`, []],
            ['space before the parenthesis', `[x] (${P})`, []],
            ['balanced parentheses in the destination', '[x](a(b)c) [y](a(b(c)d)e)', ['a(b)c', 'a(b(c)d)e']],
            ['escaped parentheses', '[x](a\\(b) [y](a\\)b)', ['a\\(b', 'a\\)b']],
            ['unbalanced parentheses', '[x](a(b)', []],
            ['space in a plain destination', '[x](a b)', []],
            ['character reference in the destination', '[x](/a&amp;b) [y](/a&b) [z](/a%20b)', ['/a&amp;b', '/a&b', '/a%20b']],
            ['label with brackets', `[a[b]c](${P})`, [P]],
            ['nested link in an image label', `![a [b](/c) d](${P})`, ['/c', P]],
            ['image in a link label', `[![x](/a)](${P})`, ['/a', P]],
            ['link inside a link label is not a link', '[a [b](/c)](/d)', ['/c']],
            ['label over several lines', `[a\nb](${P})`, [P]],
            ['emphasis around a link', `*[x](${P})* **![y](${P})**`, [P, P]],
            [
                'javascript and data URLs are destinations too',
                '[x](javascript:alert(1)) [y](data:text/html,hi) ![z](javascript:a)',
                ['javascript:alert(1)', 'data:text/html,hi', 'javascript:a'],
            ],
            ['escaped bracket', `\\[x](${P})`, []],
            ['escaped closing bracket', `[x\\](${P})`, []],
            ['several on one line', '[a](/1) [b](/2) ![c](/3)', ['/1', '/2', '/3']],
        ])('%s', (_, source, urls) => {
            expect(destinationTexts(source)).toEqual(urls);
        });
    });

    describe('reference definitions and autolinks', () => {
        it.each<[string, string, string[]]>([
            ['definition', `[x]: ${P}`, [P]],
            ['definition with angle brackets', `[x]: <${P}>`, [P]],
            ['definition with a title', `[x]: ${P} "title"`, [P]],
            ['definition with a title on the next line', `[x]: ${P}\n  'title'`, [P]],
            ['definition with the destination on the next line', `[x]:\n  ${P}`, [P]],
            ['definition with a bad title ends at the destination', `[x]: ${P} bad "title"`, []],
            ['definitions in a row', '[x]: /1\n[y]: /2\n[z]: /3', ['/1', '/2', '/3']],
            ['indented definition', `   [x]: ${P}`, [P]],
            ['indented code is not a definition', `    [x]: ${P}`, []],
            ['definition in a block quote', '> [x]: /1\n> [y]: /2', ['/1', '/2']],
            ['definition in a list', '- [x]: /1\n- [y]: /2', ['/1', '/2']],
            ['definition with an empty destination', '[x]: <> "t"', []],
            ['definition used by a reference link', `[x][ref] ![y][ref] [ref]\n\n[ref]: ${P}`, [P]],
            ['reference link without a definition', '[x][missing]', []],
            ['definition after a paragraph is text', `text\n[x]: ${P}`, []],
            ['definition followed by text', `[x]: ${P}\ntext after`, [P]],
            ['URL autolink', `<https://a.b/${P}>`, [`https://a.b/${P}`]],
            ['autolink with an unusual scheme', '<x-y.z+w:rest/\\path> <ab:c>', ['x-y.z+w:rest/\\path', 'ab:c']],
            ['email autolink has no destination', '<a@b.c> <mailto:a@b.c>', ['mailto:a@b.c']],
            ['autolink with a space is text', '<https://a b>', []],
            ['escaped autolink', '\\<https://a.b>', []],
            ['autolink in code', '`<https://a.b>`', []],
            ['autolink in a link label', '[<https://a.b>](/c)', ['https://a.b', '/c']],
            ['two autolinks', '<https://a.b>text<https://c.d>', ['https://a.b', 'https://c.d']],
        ])('%s', (_, source, urls) => {
            expect(destinationTexts(source)).toEqual(urls);
        });
    });

    describe('structure', () => {
        it.each<[string, string, string[]]>([
            ['nested lists', `- a\n  - b ![x](${P})\n    - c [y](${P})`, [P, P]],
            ['nested block quotes', `> > ![x](${P})\n> > [y](${P})`, [P, P]],
            ['list with a block quote', `1. one\n   ![x](${P})\n2. two\n\n   > [y](${P})`, [P, P]],
            ['tab after the list marker', `-\t![x](${P})`, [P]],
            ['tab after the block quote marker', `>\t![x](${P})`, [P]],
            ['nested list with a tab', '* [x](/1)\n\t* [y](/2)', ['/1', '/2']],
            ['list with two digit marker', '10. [x](/a)\n    [y](/b)', ['/a', '/b']],
            ['headings', `# ![x](${P})\n\n## [y](${P}) ##\n\n#\t[z](${P})`, [P, P, P]],
            ['setext headings', `Title ![x](${P})\n===\n\nTitle\n[y](${P})\n---`, [P, P]],
            ['heading with seven hashes is text', `####### [x](${P})`, [P]],
            ['block quote heading', `> # [x](${P})`, [P]],
            ['heading in a list', `- # [x](${P})`, [P]],
            ['table-like text', `| a | b |\n|---|---|\n| ![x](${P}) | [y](${P}) |`, [P, P]],
            ['thematic breaks', `---\n![x](${P})\n---`, [P]],
            ['hard breaks', '[x](/1)  \n[y](/2)\\\n[z](/3)', ['/1', '/2', '/3']],
            ['definition then an indented line continues the paragraph', '[r]: /d\n    [x](/a)', ['/d', '/a']],
            ['paragraph continuation lines', `a\n   b ![x](${P})\n      c [y](${P})`, [P, P]],
            ['unicode and astral characters', `😀![x](${P}) é [y](${P})`, [P, P]],
            ['leading white space characters', `\f![x](${P})\n\v[y](${P})`, [P, P]],
            ['null character', `\0![x](${P})`, [P]],
            ['empty and tiny inputs', '', []],
            ['lone brackets', '[ ] ( ) ![ ]( [', []],
        ])('%s', (_, source, urls) => {
            expect(destinationTexts(source)).toEqual(urls);
        });
    });

    describe('code, comments and raw HTML blocks', () => {
        it.each<[string, string, string[]]>([
            ['code span', `\`![x](${P})\` ![y](${P})`, [P]],
            ['code span over lines', `\`a\n![x](${P})\` ![y](${P})`, [P]],
            ['double backticks', `\`\` \` ![x](${P}) \`\` ![y](${P})`, [P]],
            ['unclosed backtick is text', `\`unclosed ![x](${P})`, [P]],
            ['escaped backtick', `\\\`![x](${P})\``, [P]],
            ['fenced code', `\`\`\`\n![x](${P})\n\`\`\`\n![y](${P})`, [P]],
            ['tilde fence with info', `~~~ js\n[x](${P})\n~~~`, []],
            ['indented code', `    [x](${P})\n\n[y](${P})`, [P]],
            ['unclosed fence', '```\nunclosed fence ![x](/a)', []],
            ['fence in a block quote', `> \`\`\`\n> ![x](${P})\n> \`\`\`\n> ![y](${P})`, [P]],
            ['fence in a list', `- \`\`\`\n  ![x](${P})\n  \`\`\`\n- ![y](${P})`, [P]],
            ['HTML comment block', `<!-- ![x](${P}) -->![y](${P})`, []],
            ['comment around a tag', `text <!-- <img src="${P}"> --> <img src="${P}">`, [P]],
            ['empty comment', `<!---->![y](${P})`, []],
            ['comment over lines', `<!--\n![x](${P})\n<img src="${P}">\n-->`, []],
            ['div block hides Markdown', `<div>\n![x](${P})\n</div>`, []],
            ['blank line ends the HTML block', `<div>\n\n![x](${P})\n\n</div>`, [P]],
            ['pre block', `<pre>\n![x](${P})\n<a href=${P}>\n</pre>\n![y](${P})`, [P]],
            ['script block', `<script>\n![x](${P})\n</script>\n![y](${P})`, [P]],
            ['script with an image in a string', `<script>const example = '<img src="${P}">';</script>`, []],
            ['style block', `<style>a{background:url(${P})}</style>![x](${P})`, []],
            ['textarea', `<textarea>![x](${P}) <img src="${P}"></textarea> ![y](${P})`, []],
        ])('%s', (_, source, urls) => {
            expect(destinationTexts(source)).toEqual(urls);
        });
    });

    describe('HTML tags', () => {
        it.each<[string, string, string[]]>([
            ['img double quotes', `<img src="${P}">`, [P]],
            ['img single quotes', `<img src='${P}'>`, [P]],
            ['img unquoted', `<img src=${P}>`, [P]],
            ['upper case tag and attribute', `<IMG SRC="${P}"> <A HREF='${P}'>x</A>`, [P, P]],
            ['anchor', `<a href="${P}">x</a> <a href=${P}>y</a>`, [P, P]],
            ['attributes on several lines', `<img\n  src="${P}"\n  alt="x">`, [P]],
            ['spaces around the equals sign', `<img alt=x src = "${P}" >`, [P]],
            ['the first attribute with the name counts', `<img src="a" src="${P}">`, ['a']],
            ['other attributes are not destinations', `<img data-src="${P}" alt="${P}" src="/b">`, ['/b']],
            ['greater-than sign in an attribute value', `<img alt="a>b" src="${P}">`, [P]],
            ['slash instead of white space', `<img/src="${P}">`, [P]],
            ['no space between attributes', `<img src="${P}"alt="x"> <img src='${P}'alt=y>`, [P, P]],
            ['self closing', `<img src="${P}"/>`, [P]],
            [
                'character references are decoded in the URL',
                '<img src="&amp;/a?x=1&amp;y=2"> <a href="/a?x=1&y=2">x</a> <a href="&#x2F;api&#47;core">y</a>',
                ['&amp;/a?x=1&amp;y=2', '/a?x=1&y=2', '&#x2F;api&#47;core'],
            ],
            ['line end in the value', '<img src="a\nb">', ['a\nb']],
            ['empty value is no destination', '<img src=""> <img src> <a href>x</a> <img src=>', []],
            ['image tag counts as img', `<image src="${P}">`, [P]],
            ['svg image is not an img', `<svg><image href="${P}"/></svg>`, []],
            ['anchor in svg', `<svg><a href="${P}"><text>x</text></a></svg>`, [P]],
            ['template content is not searched', `<template><img src="${P}"></template> <img src="${P}">`, [P]],
            ['tags in a table', `<table><tr><td><img src="${P}"></td></tr></table>`, [P]],
            ['Markdown in a table', `<table><tr><td>![x](${P})</td></tr></table>`, []],
            ['inline HTML around Markdown', `<span title="[x](${P})">z</span> ![y](${P})`, [P]],
            ['image in an anchor', `<a href="${P}">![x](${P})</a> [<img src="${P}">](${P})`, [P, P, P, P]],
            ['Markdown in an attribute value is part of the tag', `<span title="![x](${P})">z</span>`, []],
            ['escaped tag', `\\<img src="${P}"> &lt;img src="${P}"&gt; \`<img src="${P}">\``, []],
            ['tag in a heading', `## <img src="${P}"> ##`, [P]],
            ['tag in a list and block quote', `- <img src="${P}">\n\n> <a href=${P}>x</a>`, [P, P]],
            ['unquoted value with Markdown characters', '<img src=a*b*c> <img src=a_b_c> <a href=a*b>x*</a>', ['a*b*c', 'a_b_c', 'a*b']],
            ['misnested anchors give one destination each', '<p><a href=x>1<p>2</a> <b><a href=y>1</b>2</a> <p><a href=z>1<p>2<a href=w>3', ['x', 'y', 'z', 'w']],
            ['code element hides tags and links', `<code><a href="${P}">x</a></code> <a href="${P}">y</a> <code>![x](${P})</code> ![y](${P})`, [P, P]],
            ['code across lines', `<code>a\n\n![x](${P})\n\n</code>`, []],
            ['pre and code', `<pre><code>![x](${P})</code></pre>`, []],
            ['unclosed code hides the rest', `a <code>b ![x](${P}) c`, []],
            ['closed code in a sentence', `a <code>b</code> ![x](${P}) c`, [P]],
            ['a script block runs to the end of the text', `<script> ![x](${P})`, []],
        ])('%s', (_, source, urls) => {
            expect(destinationTexts(source)).toEqual(urls);
        });
    });

    describe('character references', () => {
        it('decodes them in the URL of an HTML attribute but not in the source range', () => {
            const source = '<img src="&amp;/a?x=1&amp;y=2"> <a href="/a?x=1&y=2">x</a> <a href="&#x2F;api&#47;core">y</a>';
            expect(markdownFileDestinations(source).map((destination) => destination.url)).toEqual(['&/a?x=1&y=2', '/a?x=1&y=2', '/api/core']);
            expect(destinationTexts(source)).toEqual(['&amp;/a?x=1&amp;y=2', '/a?x=1&y=2', '&#x2F;api&#47;core']);
        });

        it('keeps the character references of a Markdown destination as written', () => {
            expect(markdownFileDestinations('[x](/a&amp;b)')[0].url).toBe('/a&amp;b');
        });
    });

    // The parsers behind this module changed from micromark and parse5 to markdown-it and the HTML parser of the browser. These cases are
    // where the result is better than before, and every one is deliberate.
    describe('improvements over the previous parsers', () => {
        it('finds the source of a tag that spans lines inside a block quote or list', () => {
            expect(destinationTexts(`> <img\n> src="${P}">`)).toEqual([P]);
            expect(destinationTexts(`> <img src="${P}"\n> alt="x">`)).toEqual([P]);
            expect(destinationTexts(`- <img\n  src="${P}">`)).toEqual([P]);
            expect(destinationTexts(`- <a href=${P}\n  title=x>y</a>`)).toEqual([P]);
        });

        it('keeps offsets right when the text starts with a byte order mark', () => {
            const source = `\ufeff![x](${P})`;
            expect(markdownFileDestinations(source)).toEqual([{ start: 6, end: 6 + P.length, url: P }]);
        });

        it('reports a tag that the HTML parser copies when elements are misnested only once', () => {
            expect(destinationTexts('<p><a href=x>1<p>2</a>')).toEqual(['x']);
            expect(destinationTexts('<p><a href=x>1<p>2<a href=y>3')).toEqual(['x', 'y']);
            // The copy that the parser makes after the closed pre element belongs to the anchor inside it, which is skipped.
            expect(destinationTexts('<pre>\n<a href=in>\n</pre>\n<a href=out>')).toEqual(['out']);
        });

        it('reports no destination for an attribute without a value', () => {
            expect(markdownFileDestinations("<img src> <a href> <IMG data-x='a\\)b' src/> <img src=`" + P + '`>')).toEqual([]);
        });
    });

    // Where markdown-it and micromark read the same text differently, or where the HTML parser of the browser cannot say where in the text
    // a Markdown destination lies, the behaviour below is the new one. A destination that is not found only leaves a link on the archived file.
    describe('known differences from the previous parsers', () => {
        it('reads a definition right before a setext underline or list marker as markdown-it does', () => {
            expect(destinationTexts('[f]:\n=')).toEqual(['=']);
            expect(destinationTexts('[a]:g\n-\n[f]:i')).toEqual(['g', 'i']);
            expect(destinationTexts('[x]:\r=')).toEqual(['=']);
        });

        it('treats a lone tag on a lazy continuation line in a container as paragraph text', () => {
            expect(destinationTexts('>d\n<t>\n<s>\n[](a)')).toEqual(['a']);
            expect(destinationTexts('- d\n<t>\n<s>\n[](a)')).toEqual(['a']);
            expect(destinationTexts('>d\n<t>\n    [](a)')).toEqual(['a']);
        });

        it('finds a Markdown link inside the start tag of a code, pre or script element', () => {
            expect(destinationTexts('<code[](:)>')).toEqual([':']);
            expect(destinationTexts('<pre[](")>')).toEqual(['"']);
            expect(destinationTexts('<script[](")></script>')).toEqual(['"']);
        });

        it('finds a Markdown link after an unterminated quote in the start tag of a pre element', () => {
            expect(destinationTexts('<pre><p t="</pre>\n[](")')).toEqual(['"']);
        });
    });

    describe('raw text elements inside a paragraph', () => {
        // After prose, a script, style or textarea tag is inline HTML. Closed, it hides the Markdown inside it; left open, as a stray tag in a
        // sentence, it does not hide the links after it.
        it.each(['script', 'style', 'textarea'])('keeps the links after an unclosed %s and hides those inside a closed one', (tag) => {
            expect(destinationTexts(`text <${tag}>![x](${P})</${tag}> ![y](${P})`)).toEqual([P]);
            expect(destinationTexts(`text <${tag}>![x](${P})`)).toEqual([P]);
        });

        it.each(['code', 'pre'])('hides the links after an unclosed %s, as the element has no end', (tag) => {
            expect(destinationTexts(`text <${tag}>![x](${P})`)).toEqual([]);
        });
    });

    describe('escapes', () => {
        it('keeps a backslash that does not escape anything as part of a value, and ignores one that does', () => {
            // The tag is not valid HTML in Markdown (no space between the attributes), so it is read as text and the escape rules apply to it.
            expect(destinationTexts('<IMG src="\\b"p>')).toEqual(['\\b']);
            expect(destinationTexts('<IMG src="\\)"p>')).toEqual([]);
        });
    });

    describe('definitions and list items', () => {
        it('does not read an indented line after a definition as paragraph text when it starts in a new list item', () => {
            // The first child of the list item is indented code, in which nothing is a destination.
            expect(destinationTexts(`[r]: /d\n1.     ![x](${P})`)).toEqual(['/d']);
            // In the same block the indented line continues the paragraph of the definition.
            expect(destinationTexts(`[r]: /d\n    ![x](${P})`)).toEqual(['/d', P]);
        });
    });

    describe('values that continue on container lines', () => {
        it.each([
            ['block quote', `> <img src="${P}\n> x">`],
            ['list', `- <img src="${P}\n  x">`],
            ['paragraph break', '<img src="\n\n">'],
        ])('returns no destination for a quoted value that crosses a line of a %s', (_, source) => {
            expect(markdownFileDestinations(source)).toEqual([]);
        });

        it('returns a quoted value over several lines of one paragraph', () => {
            expect(destinationTexts(`<img src="a\nb">`)).toEqual(['a\nb']);
            expect(destinationTexts(`text\n  <img src="a\n  b">`)).toEqual(['a\n  b']);
        });
    });

    describe('large documents', () => {
        it('finds one destination per line in a long paragraph and per link on a long line', () => {
            const lines = Array.from({ length: 3000 }, (_, index) => `line ${index} [x](/a/${index}.png)`);
            expect(destinationTexts(lines.join('\n'))).toEqual(lines.map((_, index) => `/a/${index}.png`));
            expect(destinationTexts('[a](/b) '.repeat(5000))).toHaveLength(5000);
        });

        it('copes with a text that contains every private-use character', () => {
            const privateUse = Array.from({ length: 0xf8ff - 0xe000 + 1 }, (_, index) => String.fromCharCode(0xe000 + index)).join('');
            // No character is left to mark destinations, so none is excluded; the links are found and nothing hangs.
            expect(destinationTexts(`${privateUse} <code>![x](${P})</code>`)).toEqual([P]);
        });
    });

    describe('safety', () => {
        /** Checks what the importer relies on: sorted, non-empty and disjoint ranges inside the text, so replacing them cannot damage anything else. */
        function expectSafe(source: string): void {
            const destinations = markdownFileDestinations(source);
            let previousEnd = 0;
            for (const { start, end, url } of destinations) {
                expect(Number.isInteger(start) && Number.isInteger(end)).toBe(true);
                expect(start).toBeGreaterThanOrEqual(previousEnd);
                expect(end).toBeGreaterThan(start);
                expect(end).toBeLessThanOrEqual(source.length);
                expect(typeof url).toBe('string');
                previousEnd = end;
            }
            // Replacing every destination by itself changes nothing, and replacing it by something else changes nothing outside the ranges.
            expect(replaceDestinations(source, (text) => text)).toBe(source);
            const outside = destinations
                .map(({ start }, index) => source.slice(index === 0 ? 0 : destinations[index - 1].end, start))
                .concat(source.slice(destinations.at(-1)?.end ?? 0));
            expect(replaceDestinations(source, () => '\u0001').split('\u0001')).toEqual(destinations.length ? outside : [source]);
            expect(markdownFileDestinations(source)).toEqual(destinations);
        }

        // Each source is valid or malformed in a way that is known: the count says how many destinations it has, as a parser that fails
        // would also pass the checks on safe ranges with none. Past the nesting limit of the parser (100 levels) nothing is read inside.
        it.each<[string, string, number]>([
            ['empty', '', 0],
            ['lone brackets', '[ ] ( ) ![ ]( [ ] [', 0],
            ['unterminated constructs', '[x](<' + P + '\n![y](' + P + ' "title\n<img src="' + P + "\n<a href='" + P, 0],
            ['unterminated tags', '<'.repeat(50) + '<a' + '<img src=' + '<!--' + '<![CDATA[' + '<?php', 0],
            ['lone surrogates and null characters', '\ud800![x](' + P + ')\udc00\0<img src="' + P + '">\0', 2],
            ['line and paragraph separators', `![x](${P})\u2028[y](${P})\u2029<img src="${P}">`, 3],
            ['astral characters before tags', '😀'.repeat(50) + `<img src="${P}">` + '😀' + `![x](${P})`, 2],
            ['many unterminated links', '[a](b'.repeat(5000), 0],
            ['many opening brackets', '['.repeat(5000) + `](${P})`, 1],
            ['deeply nested block quotes', '> '.repeat(1000) + `![x](${P})`, 0],
            ['deeply nested lists', '- '.repeat(1000) + `![x](${P})`, 0],
            ['deeply nested elements', '<div>'.repeat(1000) + `<img src="${P}">`, 1],
            ['many backticks', 'a ' + '`'.repeat(10000) + ` ![x](${P})`, 1],
            ['very long line', 'x'.repeat(300000) + `![x](${P})`, 1],
            ['very long destination', `[x](${'a'.repeat(100000)})`, 1],
            ['very long attribute value', `<img src="${'a'.repeat(100000)}">`, 1],
            ['many definitions', Array.from({ length: 2000 }, (_, index) => `[r${index}]: ${P}`).join('\n'), 2000],
        ])('returns safe ranges and the right number of destinations for %s', (_, source, count) => {
            expectSafe(source);
            expect(markdownFileDestinations(source)).toHaveLength(count);
        });

        // A small generator with fixed seeds: random documents from pieces that interact (containers, code, HTML, links, definitions).
        function randomDocuments(seed: number, count: number): string[] {
            let state = seed;
            const random = () => (state = (Math.imul(state, 1664525) + 1013904223) >>> 0) / 4294967296;
            const pick = <T>(items: T[]): T => items[Math.floor(random() * items.length)];
            const destination = () => pick([P, '/a/b.png', 'https://x.y/z', '<a b>', 'a(b)c', 'a\\)b', '#h', '', '/a%20b', '/p&amp;q']);
            const title = () => pick(['', ' "t"', " 't'", ' (t)', '\n"t"', ' bad']);
            const tag = () => pick(['img', 'a', 'IMG', 'code', 'pre', 'script', 'style', 'textarea', 'div', 'p', 'table', 'td', 'svg', 'template', 'span', 'b']);
            const inline: (() => string)[] = [
                () => `[${pick(['x', '', 'a[b]c', '`c`', '*e*', '![i](/j)'])}](${destination()}${title()})`,
                () => `![${pick(['x', '', '[l](/m)'])}](${destination()}${title()})`,
                () => `[${pick(['x', 'ref'])}]${pick(['', '[]', '[ref]'])}`,
                () => `<${pick(['https://a.b/c', 'x:y', 'a@b.c', 'http://a b'])}>`,
                () => `<${tag()}${pick(['', ` src="${destination()}"`, ` href='${destination()}'`, ` src=${destination() || 'v'}`, ' src', ' alt="x'])}${pick(['', '/'])}>`,
                () => `</${tag()}>`,
                () => pick(['`', '``', '\\', '&amp;', '&lt;', '*', '_', '<!--', '-->', '  ', 'text', 'a_b_c']),
            ];
            const block: (() => string)[] = [
                () => pick(['', '> ', '- ', '1. ', '    ', '\t', '# ']) + Array.from({ length: 1 + Math.floor(random() * 4) }, () => pick(inline)()).join(pick(['', ' ', '\n'])),
                () => `${pick(['```', '~~~', '> ```'])}\n${pick(inline)()}\n${pick(['```', '~~~', ''])}`,
                () => `[${pick(['ref', 'x'])}]:${pick([' ', '\n '])}${destination()}${title()}`,
                () => pick(['<!--', '<div>', '<pre>', '<script>', '<table>', '</div>', '-->']) + pick(inline)(),
                () => pick(['---', '===', '', '>', '-']),
            ];
            return Array.from({ length: count }, () => Array.from({ length: 1 + Math.floor(random() * 6) }, () => pick(block)()).join(pick(['\n', '\n\n', '\r\n', '\r'])));
        }

        it.each([1, 2, 3])('keeps its promises for random documents (seed %d)', (seed) => {
            for (const source of randomDocuments(seed, 150)) {
                expectSafe(source);
            }
        });
    });
});
