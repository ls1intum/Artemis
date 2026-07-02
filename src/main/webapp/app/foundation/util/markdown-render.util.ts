import type { PluginSimple } from 'markdown-it';

/**
 * Lazy boundary for the markdown rendering pipeline.
 *
 * `markdown.conversion.util.ts` statically pulls in `markdown-it`, `highlight.js`, `katex` and
 * `dompurify` (~0.5 MB). Importing it only through the dynamic `import()` below keeps those libraries
 * out of any bundle that imports *this* module (which has no heavy static imports), so they no longer
 * land in the eager `main.js`. The module promise is memoized, so the chunk is fetched once and reused.
 *
 * Use this (or the {@link file://../directives/markdown.directive.ts} `[jhiMarkdown]` directive that wraps
 * it) instead of the synchronous `htmlForMarkdown` / `ArtemisMarkdownService` from any code that is
 * reachable from app bootstrap; the synchronous variants remain for lazily-loaded routes.
 */
let markdownModulePromise: Promise<typeof import('app/foundation/util/markdown.conversion.util')> | undefined;

function loadMarkdownModule(): Promise<typeof import('app/foundation/util/markdown.conversion.util')> {
    return (markdownModulePromise ??= import('app/foundation/util/markdown.conversion.util'));
}

/**
 * Asynchronously converts markdown to sanitized HTML, lazily loading the markdown pipeline on first use.
 * Mirrors the parameters of the synchronous `htmlForMarkdown`.
 */
export async function renderMarkdownToHtml(
    markdownText?: string,
    extensions: PluginSimple[] = [],
    allowedHtmlTags?: string[],
    allowedHtmlAttributes?: string[],
    lineBreaks = false,
): Promise<string> {
    if (!markdownText) {
        return '';
    }
    const { htmlForMarkdown } = await loadMarkdownModule();
    return htmlForMarkdown(markdownText, extensions, allowedHtmlTags, allowedHtmlAttributes, lineBreaks);
}

/**
 * Asynchronously converts posting markdown to sanitized HTML, mirroring
 * `ArtemisMarkdownService.safeHtmlForPostingMarkdown`: the first paragraph before (or after) a reference
 * gets the `inline-paragraph` class so it does not introduce unintended line breaks.
 */
export async function renderPostingMarkdownToHtml(
    markdownText?: string,
    contentBeforeReference = true,
    allowedHtmlTags?: string[],
    allowedHtmlAttributes?: string[],
): Promise<string> {
    if (!markdownText) {
        return '';
    }
    const { htmlForMarkdown } = await loadMarkdownModule();
    const convertedString = htmlForMarkdown(markdownText, [], allowedHtmlTags, allowedHtmlAttributes, true);
    const paragraphPosition = contentBeforeReference ? convertedString.lastIndexOf('<p>') : convertedString.indexOf('<p>');
    if (paragraphPosition === -1) {
        // No paragraph to tag (e.g. fenced-code-only content) — return the rendered HTML unchanged.
        return convertedString;
    }
    return convertedString.slice(0, paragraphPosition) + convertedString.slice(paragraphPosition).replace('<p>', '<p class="inline-paragraph">');
}
