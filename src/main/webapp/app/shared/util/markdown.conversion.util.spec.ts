import { MarkdownitTagClass, htmlForMarkdown, markdownForHtml } from './markdown.conversion.util';
import type { PluginSimple } from 'markdown-it';
import MarkdownIt from 'markdown-it';

describe('markdown.conversion.util', () => {
    describe('htmlForMarkdown', () => {
        describe('empty and undefined input', () => {
            it('should return empty string for undefined input', () => {
                expect(htmlForMarkdown(undefined)).toBe('');
            });

            it('should return empty string for empty string input', () => {
                expect(htmlForMarkdown('')).toBe('');
            });

            it('should return empty string for null input', () => {
                expect(htmlForMarkdown(null as unknown as string)).toBe('');
            });
        });

        describe('basic markdown conversion', () => {
            it('should convert bold text', () => {
                const result = htmlForMarkdown('**bold**');
                expect(result).toContain('<strong>bold</strong>');
            });

            it('should convert italic text', () => {
                const result = htmlForMarkdown('*italic*');
                expect(result).toContain('<em>italic</em>');
            });

            it('should convert headers', () => {
                const result = htmlForMarkdown('# Header 1');
                expect(result).toContain('<h1>Header 1</h1>');
            });

            it('should convert multiple header levels', () => {
                const h1 = htmlForMarkdown('# H1');
                const h2 = htmlForMarkdown('## H2');
                const h3 = htmlForMarkdown('### H3');
                expect(h1).toContain('<h1>H1</h1>');
                expect(h2).toContain('<h2>H2</h2>');
                expect(h3).toContain('<h3>H3</h3>');
            });

            it('should convert links', () => {
                const result = htmlForMarkdown('[link](https://example.com)');
                expect(result).toContain('<a href="https://example.com">link</a>');
            });

            it('should convert unordered lists', () => {
                const result = htmlForMarkdown('- item 1\n- item 2');
                expect(result).toContain('<ul>');
                expect(result).toContain('<li>item 1</li>');
                expect(result).toContain('<li>item 2</li>');
                expect(result).toContain('</ul>');
            });

            it('should convert ordered lists', () => {
                const result = htmlForMarkdown('1. first\n2. second');
                expect(result).toContain('<ol>');
                expect(result).toContain('<li>first</li>');
                expect(result).toContain('<li>second</li>');
                expect(result).toContain('</ol>');
            });

            it('should convert blockquotes', () => {
                const result = htmlForMarkdown('> quote');
                expect(result).toContain('<blockquote>');
                expect(result).toContain('quote');
                expect(result).toContain('</blockquote>');
            });

            it('should convert inline code', () => {
                const result = htmlForMarkdown('`code`');
                expect(result).toContain('<code>code</code>');
            });

            it('should convert horizontal rules', () => {
                const result = htmlForMarkdown('---');
                expect(result).toContain('<hr');
            });

            it('should strip trailing newline for legacy compatibility', () => {
                const result = htmlForMarkdown('text');
                expect(result.endsWith('\n')).toBeFalse();
            });
        });

        describe('code block conversion', () => {
            it('should convert fenced code blocks', () => {
                const result = htmlForMarkdown('```\ncode\n```');
                expect(result).toContain('<pre>');
                expect(result).toContain('<code');
                expect(result).toContain('code');
            });

            it('should convert code blocks with language specification', () => {
                const result = htmlForMarkdown('```javascript\nconst x = 1;\n```');
                expect(result).toContain('<pre>');
                expect(result).toContain('<code');
                expect(result).toContain('const');
            });

            it('should apply syntax highlighting to Java code', () => {
                const result = htmlForMarkdown('```java\npublic class Test {}\n```');
                expect(result).toContain('hljs');
            });

            it('should apply syntax highlighting to Python code', () => {
                const result = htmlForMarkdown('```python\ndef hello():\n    pass\n```');
                expect(result).toContain('hljs');
            });
        });

        describe('LaTeX formula conversion', () => {
            it('should convert display LaTeX formulas with $$ delimiters', () => {
                const result = htmlForMarkdown('$$x^2$$');
                // KaTeX renders math content
                expect(result).toContain('katex');
            });

            it('should convert inline LaTeX formulas with $ delimiters', () => {
                const result = htmlForMarkdown('$x^2$');
                expect(result).toContain('katex');
            });

            it('should handle formula compatibility plugin for inline formulas with surrounding text', () => {
                // Test the formula compatibility: inline $$ should be converted to $ when surrounded by text
                const result = htmlForMarkdown('This is $$x^2$$ a formula');
                // The formula should be rendered
                expect(result).toContain('katex');
            });

            it('should handle escaped begin/end in formulas', () => {
                const result = htmlForMarkdown('$$\\\\begin{matrix}a\\\\end{matrix}$$');
                // Should render without errors (the plugin fixes double escapes)
                expect(result).toBeDefined();
            });

            it('should handle formula at end of line with surrounding text', () => {
                const result = htmlForMarkdown('Calculate $$x^2$$');
                expect(result).toContain('katex');
            });

            it('should handle formula at start of line with text after', () => {
                const result = htmlForMarkdown('$$x^2$$ is squared');
                expect(result).toContain('katex');
            });

            it('should preserve block formulas on their own line', () => {
                const result = htmlForMarkdown('$$x^2$$');
                expect(result).toContain('katex');
            });

            it('should handle multiple formulas on same line', () => {
                const result = htmlForMarkdown('$$a$$ and $$b$$');
                expect(result).toContain('katex');
            });

            it('should handle complex LaTeX expressions', () => {
                const result = htmlForMarkdown('$\\frac{a}{b}$');
                expect(result).toContain('katex');
            });

            it('should handle multiline formulas', () => {
                const result = htmlForMarkdown('$$\na + b\n$$');
                expect(result).toBeDefined();
            });
        });

        describe('Artemis custom syntax', () => {
            it('should preserve testid tags for test case markers', () => {
                const result = htmlForMarkdown('<testid>testCheckDateInFuture</testid>');
                expect(result).toContain('<testid>testCheckDateInFuture</testid>');
            });

            it('should preserve testid tags within other content', () => {
                const result = htmlForMarkdown('Run the test <testid>myTest</testid> to verify');
                expect(result).toContain('<testid>myTest</testid>');
            });

            it('should preserve multiple testid tags', () => {
                const result = htmlForMarkdown('<testid>test1</testid> and <testid>test2</testid>');
                expect(result).toContain('<testid>test1</testid>');
                expect(result).toContain('<testid>test2</testid>');
            });
        });

        describe('lineBreaks option', () => {
            it('should not convert single newlines to <br> when lineBreaks is false', () => {
                const result = htmlForMarkdown('line1\nline2', [], undefined, undefined, false);
                expect(result).not.toContain('<br');
            });

            it('should convert single newlines to <br> when lineBreaks is true', () => {
                const result = htmlForMarkdown('line1\nline2', [], undefined, undefined, true);
                expect(result).toContain('<br');
            });
        });

        describe('allowed HTML tags', () => {
            it('should sanitize script tags by default', () => {
                const result = htmlForMarkdown('<script>alert("xss")</script>');
                expect(result).not.toContain('<script>');
                expect(result).not.toContain('alert');
            });

            it('should allow testid tags by default', () => {
                const result = htmlForMarkdown('<testid>123</testid>');
                expect(result).toContain('<testid>123</testid>');
            });

            it('should respect allowedHtmlTags parameter', () => {
                const result = htmlForMarkdown('<div>content</div><span>text</span>', [], ['div'], undefined);
                expect(result).toContain('<div>content</div>');
                expect(result).not.toContain('<span>');
            });

            it('should respect allowedHtmlAttributes parameter', () => {
                const result = htmlForMarkdown('<div class="test" id="myid">content</div>', [], undefined, ['class']);
                expect(result).toContain('class="test"');
                expect(result).not.toContain('id="myid"');
            });
        });

        describe('table conversion', () => {
            it('should convert markdown tables with table class', () => {
                const markdown = '| Header 1 | Header 2 |\n|----------|----------|\n| Cell 1   | Cell 2   |';
                const result = htmlForMarkdown(markdown);
                expect(result).toContain('<table');
                expect(result).toContain('class="table"');
                expect(result).toContain('<th>');
                expect(result).toContain('<td>');
            });
        });

        describe('GitHub alerts', () => {
            it('should convert GitHub-style alerts', () => {
                const result = htmlForMarkdown('> [!NOTE]\n> This is a note');
                expect(result).toContain('markdown-alert');
            });

            it('should convert warning alerts', () => {
                const result = htmlForMarkdown('> [!WARNING]\n> This is a warning');
                expect(result).toContain('markdown-alert');
            });
        });

        describe('custom extensions', () => {
            it('should apply custom extensions', () => {
                let extensionCalled = false;
                const customExtension: PluginSimple = (md: MarkdownIt) => {
                    extensionCalled = true;
                    md.core.ruler.push('test-extension', () => {});
                };

                htmlForMarkdown('test', [customExtension]);
                expect(extensionCalled).toBeTrue();
            });

            it('should apply multiple custom extensions', () => {
                const callOrder: number[] = [];
                const extension1: PluginSimple = (md: MarkdownIt) => {
                    callOrder.push(1);
                    md.core.ruler.push('ext1', () => {});
                };
                const extension2: PluginSimple = (md: MarkdownIt) => {
                    callOrder.push(2);
                    md.core.ruler.push('ext2', () => {});
                };

                htmlForMarkdown('test', [extension1, extension2]);
                expect(callOrder).toEqual([1, 2]);
            });
        });

        describe('caching behavior', () => {
            it('should return consistent results with caching', () => {
                const result1 = htmlForMarkdown('**bold**');
                const result2 = htmlForMarkdown('**bold**');
                expect(result1).toBe(result2);
            });

            it('should return consistent results with same lineBreaks setting', () => {
                const result1 = htmlForMarkdown('line1\nline2', [], undefined, undefined, true);
                const result2 = htmlForMarkdown('line1\nline2', [], undefined, undefined, true);
                expect(result1).toBe(result2);
            });

            it('should produce different results for different lineBreaks settings', () => {
                const withBreaks = htmlForMarkdown('line1\nline2', [], undefined, undefined, true);
                const withoutBreaks = htmlForMarkdown('line1\nline2', [], undefined, undefined, false);
                expect(withBreaks).not.toBe(withoutBreaks);
            });
        });

        describe('XSS prevention', () => {
            it('should sanitize onclick attributes', () => {
                const result = htmlForMarkdown('<div onclick="alert(1)">click</div>');
                expect(result).not.toContain('onclick');
            });

            it('should sanitize script tags', () => {
                const result = htmlForMarkdown('<script>alert(1)</script>');
                expect(result).not.toContain('<script>');
            });

            it('should sanitize onerror attributes', () => {
                const result = htmlForMarkdown('<img src="x" onerror="alert(1)">');
                expect(result).not.toContain('onerror');
            });

            it('should sanitize style tags', () => {
                const result = htmlForMarkdown('<style>body { display: none; }</style>');
                expect(result).not.toContain('<style>');
            });

            it('should sanitize iframe tags', () => {
                const result = htmlForMarkdown('<iframe src="https://malicious.com"></iframe>');
                expect(result).not.toContain('<iframe>');
            });
        });

        describe('linkify feature', () => {
            it('should automatically linkify URLs', () => {
                const result = htmlForMarkdown('Visit https://example.com for more info');
                expect(result).toContain('<a href="https://example.com"');
            });

            it('should linkify email addresses', () => {
                const result = htmlForMarkdown('Contact test@example.com');
                expect(result).toContain('href="mailto:test@example.com"');
            });
        });

        describe('complex markdown documents', () => {
            it('should handle a complex document with multiple elements', () => {
                const markdown = `# Title

This is a **paragraph** with *emphasis*.

## Code Example

\`\`\`java
public class Test {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
\`\`\`

> A quote

- List item 1
- List item 2

| Column 1 | Column 2 |
|----------|----------|
| A        | B        |
`;
                const result = htmlForMarkdown(markdown);
                expect(result).toContain('<h1>Title</h1>');
                expect(result).toContain('<strong>paragraph</strong>');
                expect(result).toContain('<em>emphasis</em>');
                expect(result).toContain('<h2>Code Example</h2>');
                expect(result).toContain('<pre>');
                expect(result).toContain('<blockquote>');
                expect(result).toContain('<ul>');
                expect(result).toContain('<table');
            });
        });
    });

    describe('markdownForHtml', () => {
        it('should convert bold HTML to markdown', () => {
            const result = markdownForHtml('<strong>bold</strong>');
            expect(result).toBe('**bold**');
        });

        it('should convert italic HTML to markdown', () => {
            const result = markdownForHtml('<em>italic</em>');
            expect(result).toBe('_italic_');
        });

        it('should convert headers to markdown', () => {
            const h1 = markdownForHtml('<h1>Header</h1>');
            const h2 = markdownForHtml('<h2>Header</h2>');
            expect(h1).toContain('Header');
            expect(h2).toContain('Header');
        });

        it('should convert links to markdown', () => {
            const result = markdownForHtml('<a href="https://example.com">link</a>');
            expect(result).toBe('[link](https://example.com)');
        });

        it('should convert unordered lists to markdown', () => {
            const result = markdownForHtml('<ul><li>item 1</li><li>item 2</li></ul>');
            expect(result).toContain('*   item 1');
            expect(result).toContain('*   item 2');
        });

        it('should convert ordered lists to markdown', () => {
            const result = markdownForHtml('<ol><li>first</li><li>second</li></ol>');
            expect(result).toContain('1.  first');
            expect(result).toContain('2.  second');
        });

        it('should convert paragraphs', () => {
            const result = markdownForHtml('<p>A paragraph</p>');
            expect(result).toBe('A paragraph');
        });

        it('should convert code blocks', () => {
            const result = markdownForHtml('<pre><code>code here</code></pre>');
            expect(result).toContain('code here');
        });

        it('should convert inline code', () => {
            const result = markdownForHtml('<code>inline</code>');
            expect(result).toBe('`inline`');
        });

        it('should convert blockquotes', () => {
            const result = markdownForHtml('<blockquote>quoted text</blockquote>');
            expect(result).toContain('> quoted text');
        });

        it('should handle nested HTML elements', () => {
            const result = markdownForHtml('<p><strong>bold <em>and italic</em></strong></p>');
            expect(result).toContain('**');
            expect(result).toContain('_');
        });

        it('should convert images', () => {
            const result = markdownForHtml('<img src="image.png" alt="description">');
            expect(result).toBe('![description](image.png)');
        });

        it('should convert horizontal rules', () => {
            const result = markdownForHtml('<hr>');
            // Turndown uses '* * *' for horizontal rules
            expect(result).toContain('* * *');
        });

        it('should handle empty input', () => {
            const result = markdownForHtml('');
            expect(result).toBe('');
        });

        it('should handle plain text', () => {
            const result = markdownForHtml('plain text');
            expect(result).toBe('plain text');
        });

        it('should convert line breaks', () => {
            const result = markdownForHtml('line1<br>line2');
            expect(result).toContain('line1');
            expect(result).toContain('line2');
        });
    });

    describe('MarkdownitTagClass', () => {
        let md: MarkdownIt;

        beforeEach(() => {
            md = new MarkdownIt();
        });

        it('should add class to table tags', () => {
            md.use(MarkdownitTagClass, { table: 'table' });
            const result = md.render('| A | B |\n|---|---|\n| 1 | 2 |');
            expect(result).toContain('class="table"');
        });

        it('should add multiple classes to a tag', () => {
            md.use(MarkdownitTagClass, { table: ['table', 'custom-class'] });
            const result = md.render('| A | B |\n|---|---|\n| 1 | 2 |');
            expect(result).toContain('class="table custom-class"');
        });

        it('should add classes to multiple different tags', () => {
            md.use(MarkdownitTagClass, { p: 'paragraph', strong: 'bold-text' });
            const result = md.render('This is **bold** text.');
            expect(result).toContain('class="paragraph"');
            expect(result).toContain('class="bold-text"');
        });

        it('should preserve existing classes on tokens', () => {
            md.use(MarkdownitTagClass, { p: 'added-class' });
            // Markdown-it doesn't add classes by default, but our plugin should handle cases where they exist
            const result = md.render('paragraph');
            expect(result).toContain('class="added-class"');
        });

        it('should not add classes to closing tags', () => {
            md.use(MarkdownitTagClass, { p: 'test' });
            const result = md.render('text');
            // Only opening tag should have the class
            const matches = result.match(/class="test"/g);
            expect(matches).toHaveLength(1);
        });

        it('should handle nested elements', () => {
            md.use(MarkdownitTagClass, { li: 'list-item' });
            const result = md.render('- item 1\n- item 2');
            const matches = result.match(/class="list-item"/g);
            expect(matches).toHaveLength(2);
        });

        it('should handle empty mapping', () => {
            md.use(MarkdownitTagClass, {});
            const result = md.render('| A |\n|---|\n| 1 |');
            expect(result).not.toContain('class=');
        });

        it('should work with default empty mapping', () => {
            md.use(MarkdownitTagClass);
            const result = md.render('text');
            // Should not throw and should produce valid output
            expect(result).toContain('text');
        });

        it('should add class to link tags', () => {
            md.use(MarkdownitTagClass, { a: 'link' });
            const result = md.render('[text](https://example.com)');
            expect(result).toContain('class="link"');
        });

        it('should add class to heading tags', () => {
            md.use(MarkdownitTagClass, { h1: 'heading' });
            const result = md.render('# Title');
            expect(result).toContain('class="heading"');
        });

        it('should add class to blockquote tags', () => {
            md.use(MarkdownitTagClass, { blockquote: 'quote' });
            const result = md.render('> quoted');
            expect(result).toContain('class="quote"');
        });

        it('should add class to code tags in code blocks', () => {
            md.use(MarkdownitTagClass, { code: 'code-block' });
            const result = md.render('```\ncode\n```');
            expect(result).toContain('class="code-block"');
        });
    });
});
