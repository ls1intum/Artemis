import DOMPurify, { Config } from 'dompurify';
import type { PluginSimple } from 'markdown-it';
import markdownit from 'markdown-it';
import showdown from 'showdown';

/**
 * showdown will add the classes to the converted html
 * see: https://github.com/showdownjs/showdown/wiki/Add-default-classes-for-each-HTML-element
 */
const classMap: { [key: string]: string } = {
    table: 'table',
};
/**
 * extension to add css classes to html tags
 */
export const addCSSClass: PluginSimple = (md) => {
    for (const key in classMap) {
        const originalRender = md.renderer.rules[key] || md.renderer.rules.defaultRender;
        md.renderer.rules[key] = (tokens, idx, options, env, self) => {
            tokens[idx].attrPush(['class', classMap[key]]);
            return originalRender ? originalRender(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options);
        };
    }
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

    let md = markdownit({
        html: true,
        linkify: true,
        // TODO code highlight, katex, etc
    });
    for (const extension of extensions) {
        md = md.use(extension);
    }

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
    const html = DOMPurify.sanitize(mdtext, purifyParameters) as string;

    //return md.render(html);
    return html;
    /*
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
     */
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
