import { describe, expect, it } from 'vitest';
import { markdownFileDestinationSuffix, markdownFileDestinations } from './markdown-file-destinations';

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
});
