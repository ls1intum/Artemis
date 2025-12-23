import { ArtemisTextReplacementPlugin } from 'app/shared/markdown-editor/extensions/ArtemisTextReplacementPlugin';
import DOMPurify, { Config } from 'dompurify';
import type { PluginSimple } from 'markdown-it';
import type Token from 'markdown-it/lib/token.mjs';
import MarkdownItKatex from '@vscode/markdown-it-katex';
import MarkdownItHighlightjs from 'markdown-it-highlightjs';
import TurndownService from 'turndown';
import MarkdownIt from 'markdown-it';
import MarkdownItGitHubAlerts from 'markdown-it-github-alerts';

// An inline math formula has some other characters before or after the formula and uses $$ as delimiters
const inlineFormulaRegex = /.+\$\$[^$]+\$\$|\$\$[^$]+\$\$.+/g;

class FormulaCompatibilityPlugin extends ArtemisTextReplacementPlugin {
    replaceText(text: string): string {
        return text
            .split('\n')
            .map((line) => {
                if (line.match(inlineFormulaRegex)) {
                    line = line.replace(/\$\$/g, '$');
                }
                if (line.includes('\\\\begin') || line.includes('\\\\end')) {
                    line = line.replaceAll('\\\\begin', '\\begin').replaceAll('\\\\end', '\\end');
                }
                return line;
            })
            .join('\n');
    }
}
const formulaCompatibilityPlugin = new FormulaCompatibilityPlugin();

const turndownService = new TurndownService();

/**
 * Converts markdown into html (string) and sanitizes it. Does NOT declare it as safe to bypass further security
 * Note: If possible, please use safeHtmlForMarkdown
 *
 * @param {string} markdownText the original Markdown text
 * @param extensions to use for markdown parsing
 * @param {string[]} allowedHtmlTags to allow during sanitization
 * @param {string[]} allowedHtmlAttributes to allow during sanitization
 * @param {boolean} lineBreaks to indicate if line breaks should be added
 * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
 */
export function htmlForMarkdown(
    markdownText?: string,
    extensions: PluginSimple[] = [],
    allowedHtmlTags: string[] | undefined = undefined,
    allowedHtmlAttributes: string[] | undefined = undefined,
    lineBreaks: boolean = false,
): string {
    if (!markdownText || markdownText === '') {
        return '';
    }

    const markdownIt = MarkdownIt({
        html: true,
        linkify: true,
        breaks: lineBreaks, // Avoid line breaks after tasks
    });
    for (const extension of extensions) {
        markdownIt.use(extension);
    }

    // Add default extensions (Code Highlight, Latex, Alerts)
    markdownIt
        // Code Highlight
        .use(MarkdownItHighlightjs)
        .use(formulaCompatibilityPlugin.getExtension())
        // Latex formulas
        .use(MarkdownItKatex, {
            enableMathInlineInHtml: true,
        })
        // Github like alerts inside Markdown
        .use(MarkdownItGitHubAlerts)
        // Add custom html classes to be allowed it markdown
        .use(MarkdownitTagClass, {
            table: 'table',
        });

    let markdownRender = markdownIt.render(markdownText);
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
    return turndownService.turndown(htmlText);
}

type TagClassMapping = { [key: string]: string | string[] };

/**
 * Takes the markdown-it tokens and assigns classes to each token
 *
 * @param tokens Tokens injected by the markdown-it plugin
 * @param mapping Tag to class mapping
 */
function setTokenClasses(tokens: Token[], mapping: TagClassMapping = {}): void {
    tokens.forEach((token) => {
        /**
         * `token.nesting` is a number referring to the nature of the tag.
         *
         * - `1` means the tag is opening
         * - `0` means the tag is self-closing
         * - `-1` means the tag is closing
         *
         * @see https://github.com/markdown-it/markdown-it/blob/2e31d3430187d2eee1ba120c954783eebb93b4e8/lib/token.js#L44-L53
         **/
        const isOpeningTag = token.nesting !== -1;

        if (isOpeningTag && mapping[token.tag]) {
            const existingClassAttr = token.attrGet('class') || '';
            const existingClasses = existingClassAttr.split(' ').filter(Boolean);
            const givenClasses = mapping[token.tag];

            const newClasses = [...existingClasses, ...(Array.isArray(givenClasses) ? givenClasses : [givenClasses])];

            token.attrSet('class', newClasses.join(' ').trim());
        }

        // If the tag has any nested children, assign classes to those also
        if (token.children) {
            setTokenClasses(token.children, mapping);
        }
    });
}

/**
 * Markdown-it plugin to assign CSS classes to specific tags.
 *
 * @param markdown Instance of markdown-it
 * @param mapping Mapping of tags to CSS classes
 */
export function MarkdownitTagClass(markdown: MarkdownIt, mapping: TagClassMapping = {}): void {
    markdown.core.ruler.push('markdownit-tag-class', (state) => {
        setTokenClasses(state.tokens, mapping);
    });
}
