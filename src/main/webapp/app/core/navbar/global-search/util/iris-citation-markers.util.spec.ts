import { describe, expect, it } from 'vitest';
import { parseCitationNumbers, renderCitationMarkers } from './iris-citation-markers.util';

describe('renderCitationMarkers', () => {
    it('converts a single marker into a citation chip element', () => {
        const result = renderCitationMarkers('The quiz is worth 4 points.[2]', 3);
        expect(result.html).toBe('The quiz is worth 4 points.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('renders a run of consecutive markers as one chip per source', () => {
        // One hover target and one link per source: a combined chip could neither say what source 3
        // alone supports nor open anything but the first of them.
        const result = renderCitationMarkers('Composition beats inheritance.[1][3]', 3);
        expect(result.html).toBe(
            'Composition beats inheritance.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup><sup class="iris-cite" data-n="3" role="link" tabindex="0">3</sup>',
        );
        expect([...result.citedNumbers]).toEqual([1, 3]);
    });

    it('gives every chip in a run exactly one source number', () => {
        const result = renderCitationMarkers('Claim.[1][2][3]', 3);
        const numbers = [...(result.html ?? '').matchAll(/data-n="([^"]*)"/g)].map((match) => match[1]);
        expect(numbers).toEqual(['1', '2', '3']);
    });

    it('deduplicates repeated numbers inside a run', () => {
        const result = renderCitationMarkers('Claim.[1][1]', 3);
        expect(result.html).toBe('Claim.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
    });

    it('drops out-of-range markers and removes a run left empty', () => {
        const result = renderCitationMarkers('Wrong.[9] Right.[2]', 3);
        expect(result.html).toBe('Wrong. Right.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('keeps separate runs as separate chips', () => {
        const result = renderCitationMarkers('A.[1] B.[1]', 3);
        expect(result.html).toBe('A.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup> B.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
    });

    it('passes a markerless answer through untouched', () => {
        const result = renderCitationMarkers('Plain answer with [brackets] but no numbers.', 3);
        expect(result.html).toBe('Plain answer with [brackets] but no numbers.');
        expect(result.citedNumbers.size).toBe(0);
    });

    it('passes undefined through untouched', () => {
        const result = renderCitationMarkers(undefined, 3);
        expect(result.html).toBeUndefined();
        expect(result.citedNumbers.size).toBe(0);
    });

    it('strips nothing when there are no sources to index into', () => {
        const result = renderCitationMarkers('Claim.[1]', 0);
        expect(result.html).toBe('Claim.[1]');
        expect(result.citedNumbers.size).toBe(0);
    });

    it('leaves a bracketed index inside an inline code span untouched', () => {
        const result = renderCitationMarkers('Access the first element with `list[0]`.[2]', 2);
        expect(result.html).toBe('Access the first element with `list[0]`.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('leaves a bracketed index inside a double-backtick span untouched', () => {
        // CommonMark lets a code span use a longer backtick run so its content can contain a literal
        // backtick; a regex that only recognizes single backticks would stop at that interior backtick
        // and leak the rest, including the bracketed index, as citable prose.
        const result = renderCitationMarkers('Escape a backtick with ``list[0] contains a ` character``.[2]', 2);
        expect(result.html).toBe('Escape a backtick with ``list[0] contains a ` character``.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('leaves a bracketed index inside a code span that wraps onto the next line untouched', () => {
        // CommonMark folds a line break inside a code span to a space at render time, so a span can
        // legitimately cross one newline within the same paragraph; excluding newlines entirely would
        // stop at the break and leak the rest, including the bracketed index, as citable prose.
        const answer = 'Access it with `list[0]\ncontains the first item`.[2]';
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toBe('Access it with `list[0]\ncontains the first item`.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('leaves a bracketed index inside an indented code block untouched', () => {
        const answer = ['See below.[1]', '', '    const x = values[1];', '    return x;', '', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('    const x = values[1];\n    return x;');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('protects an indented code block at the very start of the answer', () => {
        const answer = ['    values[1] = 2;', '', 'Explained above.[1]'].join('\n');
        const result = renderCitationMarkers(answer, 1);
        expect(result.html).toContain('    values[1] = 2;');
        expect(result.html).toContain('Explained above.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
    });

    it('protects an indented code block that uses a tab instead of four spaces', () => {
        // A tab advances to the next 4-column stop on its own, so CommonMark counts it as satisfying
        // the indentation requirement the same as 4 literal spaces; a character-count check would miss it.
        const answer = ['See below.[1]', '', '\tconst x = values[1];', '', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('\tconst x = values[1];');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
    });

    it('recognizes a whitespace-only line as the blank line before an indented block', () => {
        // A line containing only trailing spaces is still a blank line in CommonMark; requiring a bare
        // "\n\n" would miss the block that follows one and leak its content, including values[1], as prose.
        const answer = 'See below.[1]\n   \n    const x = values[1];\n\nDone.[2]';
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('    const x = values[1];');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
    });

    it('does not let an unclosed backtick swallow a later paragraph as code', () => {
        // A code span's content may cross one line break but must not cross a blank line: CommonMark
        // inline parsing never spans a paragraph boundary either way, and without this bound one stray
        // unclosed backtick could silently eat every following paragraph's citations as "code".
        const answer = 'A stray backtick ` appears here.[1]\n\nA new paragraph follows.[2]';
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toBe(
            'A stray backtick ` appears here.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>\n\nA new paragraph follows.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>',
        );
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('leaves a tilde-fenced code block untouched', () => {
        const answer = ['See the loop below.[1]', '~~~python', 'for i in range(3):', '    print(items[i])', '~~~', 'Iteration order matches the list.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('~~~python\nfor i in range(3):\n    print(items[i])\n~~~');
        expect(result.html).toContain('See the loop below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Iteration order matches the list.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('leaves a bracketed index inside a four-backtick fence untouched', () => {
        // A fence needs a longer delimiter than 3 backticks precisely when its own content contains a
        // triple-backtick span; the delimiter-length backreference must track that, not assume exactly 3.
        const answer = ['See below.[1]', '````python', 'print(items[0])  # ```not a fence```', '````', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('````python\nprint(items[0])  # ```not a fence```\n````');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('does not close a fence early on a delimiter embedded mid-line inside its own content', () => {
        // A closing fence must be a legal closing-fence LINE (the delimiter alone, give or take
        // whitespace) — not merely the same characters appearing anywhere later in the content. A quoted
        // string literal containing the fence delimiter must not end the block early and leak the rest,
        // including a real bracketed expression on a later line, as citable prose.
        const answer = ['See below.[1]', '```python', 'const marker = "```";', 'const x = values[1];', '```', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('```python\nconst marker = "```";\nconst x = values[1];\n```');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('does not close a tilde fence early on a delimiter embedded mid-line inside its own content', () => {
        const answer = ['See below.[1]', '~~~python', 'const marker = "~~~";', 'const x = values[1];', '~~~', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('~~~python\nconst marker = "~~~";\nconst x = values[1];\n~~~');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('closes a tilde fence on a longer closing run than the opening', () => {
        // CommonMark permits a closing run with AT LEAST as many characters as the opening — a ~~~
        // block can legally close with ~~~~. An exact-length-only backreference would leave it
        // unclosed, falling through and exposing values[1] inside it as citable prose.
        const answer = ['See below.[1]', '~~~python', 'const x = values[1];', '~~~~', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('~~~python\nconst x = values[1];\n~~~~');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('closes a backtick fence on a longer closing run than the opening', () => {
        const answer = ['See below.[1]', '```python', 'items[0] = "```"', '````', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('```python\nitems[0] = "```"\n````');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('protects a backtick fence with no closing line through the end of the answer', () => {
        // CommonMark treats end-of-document as an implicit close for an unterminated fence. This matters
        // because the answer streams in sentence by sentence: a partial draft can legitimately have
        // emitted an opening fence but not its closer yet, and the code inside it must stay protected.
        const answer = ['See below.[1]', '```python', 'const x = values[1];'].join('\n');
        const result = renderCitationMarkers(answer, 1);
        expect(result.html).toContain('```python\nconst x = values[1];');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect([...result.citedNumbers]).toEqual([1]);
    });

    it('protects a tilde fence with no closing line through the end of the answer', () => {
        const answer = ['See below.[1]', '~~~python', 'const x = values[1];'].join('\n');
        const result = renderCitationMarkers(answer, 1);
        expect(result.html).toContain('~~~python\nconst x = values[1];');
        expect(result.html).toContain('See below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect([...result.citedNumbers]).toEqual([1]);
    });

    it('treats a mid-line triple-backtick run as an inline span, not an unanchored fence', () => {
        // A fence opener is only legal at the start of a line. A triple-backtick inline span used
        // mid-sentence must fall through to the exact-length inline-span alternative instead — an
        // unanchored fence alternative would treat it as an unterminated fence opener (its own closer
        // is mid-line too, so no legal closing FENCE LINE ever follows) and, via the EOF fallback
        // above, swallow everything after it, including the real [2] citation.
        const answer = 'Use ```values[1]``` here.[2]';
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('```values[1]```');
        expect(result.html).toContain('here.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('still opens a fence at the very start of the answer', () => {
        const answer = ['```python', 'const x = values[1];', '```', 'Done.[1]'].join('\n');
        const result = renderCitationMarkers(answer, 1);
        expect(result.html).toContain('```python\nconst x = values[1];\n```');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect([...result.citedNumbers]).toEqual([1]);
    });

    it('leaves a fenced code block untouched, including a real citation-shaped marker after it', () => {
        const answer = ['See the loop below.[1]', '```python', 'for i in range(3):', '    print(items[i])', '```', 'Iteration order matches the list.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('```python\nfor i in range(3):\n    print(items[i])\n```');
        expect(result.html).toContain('See the loop below.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Iteration order matches the list.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('separates a marker from a standalone display-math equation it directly follows', () => {
        // @vscode/markdown-it-katex only recognizes a display block's closing $$ when it is the
        // last thing on its line; a marker chip glued directly onto it (as the prompt instructs:
        // "directly after the claim") makes the scanner miss the closer and swallow everything
        // after it as unparsed "math" up to the next accidental $$ pair, producing a KaTeX parse
        // error instead of a citation. Separating them onto their own lines keeps the equation a
        // legal, closed display block.
        const answer = ['The equation is given as:', '', '$$\\hat{y}_i = \\theta x$$[1]', '', 'Done.[2]'].join('\n');
        const result = renderCitationMarkers(answer, 2);
        expect(result.html).toContain('$$\\hat{y}_i = \\theta x$$\n<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
        expect(result.html).toContain('Done.<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        expect([...result.citedNumbers]).toEqual([1, 2]);
    });

    it('leaves a marker after inline display math sharing its line with other prose untouched', () => {
        // The prompt's own inline example ("the parameter $$\theta$$") shares its line with prose,
        // so it never reaches markdown-it-katex's line-alone display-block scanner; a marker here
        // is not the sole content of the line and must not be pulled onto its own line.
        const answer = 'The parameter $$\\theta$$[1] controls the slope.';
        const result = renderCitationMarkers(answer, 1);
        expect(result.html).toBe('The parameter $$\\theta$$<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup> controls the slope.');
        expect([...result.citedNumbers]).toEqual([1]);
    });
});

describe('parseCitationNumbers', () => {
    it('parses the space-separated data attribute', () => {
        expect(parseCitationNumbers('1 3')).toEqual([1, 3]);
    });

    it('ignores garbage values', () => {
        expect(parseCitationNumbers('1 x 0 -2')).toEqual([1]);
    });

    it('returns empty for a missing attribute', () => {
        expect(parseCitationNumbers(undefined)).toEqual([]);
    });
});
