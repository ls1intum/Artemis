import showdown from 'showdown';
import showdownKatex from 'showdown-katex';
import showdownHighlight from 'showdown-highlight';
import DOMPurify, { Config } from 'dompurify';

/**
 * showdown will add the classes to the converted html
 * see: https://github.com/showdownjs/showdown/wiki/Add-default-classes-for-each-HTML-element
 */
const classMap: { [key: string]: string } = {
    table: 'table',
};
/**
 * extension to add css classes to html tags
 * see: https://github.com/showdownjs/showdown/wiki/Add-default-classes-for-each-HTML-element
 */
export const addCSSClass = Object.keys(classMap).map((key) => ({
    type: 'output',
    regex: new RegExp(`<${key}(.*)>`, 'g'),
    replace: `<${key} class="${classMap[key]}" $1>`,
}));

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
    extensions: showdown.ShowdownExtension[] = [],
    allowedHtmlTags: string[] | undefined = undefined,
    allowedHtmlAttributes: string[] | undefined = undefined,
): string {
    if (!markdownText || markdownText === '') {
        return '';
    }
    const converter = new showdown.Converter({
        parseImgDimensions: true,
        headerLevelStart: 3,
        simplifiedAutoLink: true,
        strikethrough: true,
        tables: true,
        openLinksInNewWindow: true,
        backslashEscapesHTMLTags: true,
        extensions: [...extensions, showdownKatex(), showdownHighlight({ pre: true }), ...addCSSClass],
    });
    const html = converter.makeHtml(markdownText);
    const purifyParameters = {} as Config;
    // Prevents sanitizer from deleting <testid>id</testid>
    purifyParameters['ADD_TAGS'] = ['testid'];
    if (allowedHtmlTags) {
        purifyParameters['ALLOWED_TAGS'] = allowedHtmlTags;
    }
    if (allowedHtmlAttributes) {
        purifyParameters['ALLOWED_ATTR'] = allowedHtmlAttributes;
    }
    return DOMPurify.sanitize(html, purifyParameters) as string;
}

export function markdownForHtml(htmlText: string): string {
    const converter = new showdown.Converter({
        parseImgDimensions: true,
        headerLevelStart: 3,
        simplifiedAutoLink: true,
        strikethrough: true,
        tables: true,
        openLinksInNewWindow: true,
        backslashEscapesHTMLTags: true,
    });
    return converter.makeMarkdown(htmlText);
}
