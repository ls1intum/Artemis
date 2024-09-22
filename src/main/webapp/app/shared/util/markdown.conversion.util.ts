import DOMPurify, { Config } from 'dompurify';
import type { PluginSimple } from 'markdown-it';
import markdownIt from 'markdown-it';
import markdownItClass from 'markdown-it-class';
import markdownItKatex from '@vscode/markdown-it-katex';
import markdown_it_highlightjs from 'markdown-it-highlightjs';
import TurndownService from 'turndown';

/**
 * Add these classes to the converted html.
 */
const classMap: { [key: string]: string } = {
    table: 'table',
};

// An inline math formula has some other characters before or after the formula and uses $$ as delimiters
const inlineFormulaRegex = /(?:.+\$\$[^\$]+\$\$)|(?:\$\$[^\$]+\$\$.+)/g;
const formulaCompatibilityPlugin: PluginSimple = (md) => {
    md.core.ruler.before('inline', 'latex-inline-migrator', (state) => {
        // markdownItKatex always creates a big block formula if $$ is used as a deliminator
        // which is different from the showdown behavior and could break existing exercises.
        // So we replace these with $formula$ to make them inline.
        state.tokens.forEach((token) => {
            if (token.content.match(inlineFormulaRegex)) {
                token.content = token.content.replace(/\$\$/g, '$');
            } else if (token.content.includes('\\\\begin') || token.content.includes('\\\\end')) {
                token.content = token.content.replaceAll('\\\\begin', '\\begin').replaceAll('\\\\end', '\\end');
            }
        });
    });
};

/**
 * Converts markdown into html (string) and sanitizes it. Does NOT declare it as safe to bypass further security
 * Note: If possible, please use safeHtmlForMarkdown
 *
 * @param {string} markdownText the original Markdown text
 * @param extensions to use for markdown parsing
 * @param {string[]} allowedHtmlTags to allow during sanitization
 * @param {string[]} allowedHtmlAttributes to allow during sanitization
 * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
 */
export function htmlForMarkdown(
    markdownText?: string,
    extensions: PluginSimple[] = [],
    allowedHtmlTags: string[] | undefined = undefined,
    allowedHtmlAttributes: string[] | undefined = undefined,
): string {
    if (!markdownText || markdownText === '') {
        return '';
    }

    const md = markdownIt({
        html: true,
        linkify: true,
        breaks: false, // Avoid line breaks after tasks
    });
    for (const extension of extensions) {
        md.use(extension);
    }

    // Add default extensions (Code Highlight, Latex)
    md.use(markdown_it_highlightjs)
        .use(formulaCompatibilityPlugin)
        .use(markdownItKatex, {
            enableMathInlineInHtml: true,
        })
        .use(markdownItClass, classMap);
    let markdownRender = md.render(markdownText);
    if (markdownRender.endsWith('\n')) {
        // Keep legacy behavior from showdown where the output does not end with \n.
        // This is needed because e.g. for quiz questions, we render the markdown in multiple small parts and then concatenate them.
        markdownRender = markdownRender.slice(0, -1);
    }

    const purifyParameters = {} as Config;
    // Prevents sanitizer from deleting <testid>id</testid>
    purifyParameters['ADD_TAGS'] = ['testid'];
    if (allowedHtmlTags) {
        purifyParameters['ALLOWED_TAGS'] = allowedHtmlTags;
    }
    if (allowedHtmlAttributes) {
        purifyParameters['ALLOWED_ATTR'] = allowedHtmlAttributes;
    }
    return DOMPurify.sanitize(markdownRender, purifyParameters) as string;
}

export function markdownForHtml(htmlText: string): string {
    const turndownService = new TurndownService();
    return turndownService.turndown(htmlText);
}
