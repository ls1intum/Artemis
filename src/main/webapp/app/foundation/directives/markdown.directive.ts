import { Directive, effect, inject, input, output, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import type { MarkdownItPlugin } from 'app/foundation/util/markdown-it.types';
import { renderMarkdownToHtml } from 'app/foundation/util/markdown-render.util';

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
    readonly markdownExtensions = input<MarkdownItPlugin[]>([]);
    /** Restricts the HTML tags kept during sanitization. */
    readonly markdownAllowedTags = input<string[]>();
    /** Restricts the HTML attributes kept during sanitization. */
    readonly markdownAllowedAttributes = input<string[]>();
    /** Renders single line breaks as `<br>`. */
    readonly markdownLineBreaks = input<boolean>(false);
    /** Emits after the current markdown input has been converted, allowing layout-dependent consumers to react to the lazy render. */
    readonly markdownRendered = output<void>();

    protected readonly renderedHtml = signal<SafeHtml>('');
    private renderGeneration = 0;

    constructor() {
        effect(() => {
            const text = this.jhiMarkdown();
            const extensions = this.markdownExtensions();
            const allowedTags = this.markdownAllowedTags();
            const allowedAttributes = this.markdownAllowedAttributes();
            const lineBreaks = this.markdownLineBreaks();

            // Bump the generation counter first so that an in-flight render from a previous value is discarded even when
            // the content is cleared while that render is still pending.
            const generation = ++this.renderGeneration;
            if (!text) {
                this.renderedHtml.set('');
                this.markdownRendered.emit();
                return;
            }

            const htmlPromise = renderMarkdownToHtml(text, extensions, allowedTags, allowedAttributes, lineBreaks);
            htmlPromise
                .then((html) => {
                    // Ignore a render whose inputs were superseded before the lazy chunk/conversion resolved.
                    if (generation === this.renderGeneration) {
                        this.renderedHtml.set(this.sanitizer.bypassSecurityTrustHtml(html));
                        this.markdownRendered.emit();
                    }
                })
                .catch(() => {
                    // The lazy chunk failed to load or rendering threw — clear rather than leak an unhandled rejection.
                    if (generation === this.renderGeneration) {
                        this.renderedHtml.set('');
                    }
                });
        });
    }
}
