import DOMPurify, { Config } from 'dompurify';
import type { PluginSimple } from 'markdown-it';
import markdownIt from 'markdown-it';
// @ts-expect-error library is not typed
import markdownItClass from 'markdown-it-class';
// @ts-expect-error library is not typed
import markdownItKatex from '@iktakahiro/markdown-it-katex';
import markdown_it_highlightjs from 'markdown-it-highlightjs';
import TurndownService from 'turndown';

/**
 * Add these classes to the converted html.
 */
const classMap: { [key: string]: string } = {
    table: 'table',
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

    let md = markdownIt({
        html: true,
        linkify: true,
        breaks: true,
    });
    for (const extension of extensions) {
        md = md.use(extension);
    }

    // Add default extensions (Code Highlight, Latex)
    md = md.use(markdown_it_highlightjs).use(markdownItKatex).use(markdownItClass, classMap);

    const mdtext = md.render(markdownText);

    const purifyParameters = {} as Config;
    // Prevents sanitizer from deleting <testid>id</testid>
    purifyParameters['ADD_TAGS'] = ['testid'];
    if (allowedHtmlTags) {
        purifyParameters['ALLOWED_TAGS'] = allowedHtmlTags;
    }
    if (allowedHtmlAttributes) {
        purifyParameters['ALLOWED_ATTR'] = allowedHtmlAttributes;
    }
    return DOMPurify.sanitize(mdtext, purifyParameters) as string;
}

export function markdownForHtml(htmlText: string): string {
    const turndownService = new TurndownService();
    return turndownService.turndown(htmlText);
}
