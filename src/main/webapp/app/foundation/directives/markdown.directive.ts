import { Directive, effect, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import type { PluginSimple } from 'markdown-it';
import { renderMarkdownToHtml, renderPostingMarkdownToHtml } from 'app/foundation/util/markdown-render.util';

/**
 * Renders markdown into the host element's `innerHTML`, lazily loading the markdown pipeline
 * (`markdown-it` + `highlight.js` + `katex` + `dompurify`) on first use so it stays out of the eager
 * bundle. Drop-in replacement for `[innerHTML]="text | htmlForMarkdown"`: keep the same host element
 * (and its classes) and switch the binding to `[jhiMarkdown]="text"`.
 *
 * The markdown is sanitized by DOMPurify inside the conversion util; the result is marked as trusted
 * HTML so Angular does not strip it again (matching the former `safeHtmlForMarkdown` behavior).
 *
 * @example `<div class="markdown-preview" [jhiMarkdown]="exercise.problemStatement"></div>`
 * @example posting content: `<span [jhiMarkdown]="post.content" [markdownPosting]="true"></span>`
 */
@Directive({
    selector: '[jhiMarkdown]',
    host: { '[innerHTML]': 'renderedHtml()' },
})
export class MarkdownDirective {
    private readonly sanitizer = inject(DomSanitizer);

    /** The markdown source text to render. */
    readonly jhiMarkdown = input<string>();
    /** Additional markdown-it plugins to apply (rarely needed). */
    readonly markdownExtensions = input<PluginSimple[]>([]);
    /** Restricts the HTML tags kept during sanitization. */
    readonly markdownAllowedTags = input<string[]>();
    /** Restricts the HTML attributes kept during sanitization. */
    readonly markdownAllowedAttributes = input<string[]>();
    /** Renders single line breaks as `<br>`. */
    readonly markdownLineBreaks = input<boolean>(false);
    /** Uses the posting-content rendering (adds the `inline-paragraph` class around a reference). */
    readonly markdownPosting = input<boolean>(false);
    /** For posting content: whether this part is before (true) or after (false) a reference. */
    readonly markdownContentBeforeReference = input<boolean>(true);

    protected readonly renderedHtml = signal<SafeHtml>('');
    private renderToken = 0;

    constructor() {
        effect(() => {
            const text = this.jhiMarkdown();
            const extensions = this.markdownExtensions();
            const allowedTags = this.markdownAllowedTags();
            const allowedAttributes = this.markdownAllowedAttributes();
            const lineBreaks = this.markdownLineBreaks();
            const posting = this.markdownPosting();
            const contentBeforeReference = this.markdownContentBeforeReference();

            // Bump the token first so that an in-flight render from a previous value is discarded even when
            // the content is cleared while that render is still pending.
            const token = ++this.renderToken;
            if (!text) {
                this.renderedHtml.set('');
                return;
            }

            const htmlPromise = posting
                ? renderPostingMarkdownToHtml(text, contentBeforeReference, allowedTags, allowedAttributes)
                : renderMarkdownToHtml(text, extensions, allowedTags, allowedAttributes, lineBreaks);
            htmlPromise
                .then((html) => {
                    // Ignore a render whose inputs were superseded before the lazy chunk/conversion resolved.
                    if (token === this.renderToken) {
                        this.renderedHtml.set(this.sanitizer.bypassSecurityTrustHtml(html));
                    }
                })
                .catch(() => {
                    // The lazy chunk failed to load or rendering threw — clear rather than leak an unhandled rejection.
                    if (token === this.renderToken) {
                        this.renderedHtml.set('');
                    }
                });
        });
    }
}
