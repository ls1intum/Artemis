import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

/**
 * Schemes that may be loaded into an embedding context. Deliberately just the two web schemes: the
 * callers of this pipe are video embeds and an LTI form target, and nothing else needs to be framed.
 */
const EMBEDDABLE_PROTOCOLS: ReadonlySet<string> = new Set(['http:', 'https:']);

@Pipe({ name: 'safeResourceUrl' })
export class SafeResourceUrlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Marks a resource URL as safe so Angular renders it in an embedding context, e.g. `<iframe src>`,
     * `<script src>` or a form `action`.
     *
     * Angular refuses to sanitize a resource URL at all — the context is too dangerous for a rewrite to
     * be meaningful — so the pipe validates the scheme itself rather than trusting the caller. Without
     * that check any caller passing user-controlled data turns `javascript:` into script execution in
     * the embedding context. Validating here makes the pipe safe regardless of the caller, the same
     * reasoning {@link SafeHtmlPipe} follows.
     *
     * @param value The resource URL to render. A relative URL is resolved against the document base and
     *                  therefore inherits the page's scheme. Anything nullish, unparseable, or carrying a
     *                  scheme outside {@link EMBEDDABLE_PROTOCOLS} yields an empty URL, which renders as
     *                  an empty frame instead of executing.
     */
    transform(value: string | undefined | null): SafeResourceUrl {
        return this.sanitizer.bypassSecurityTrustResourceUrl(this.isEmbeddable(value) ? value! : '');
    }

    private isEmbeddable(value: string | undefined | null): boolean {
        if (!value) {
            return false;
        }
        try {
            // The base makes relative URLs resolvable; without it `new URL` rejects them outright.
            return EMBEDDABLE_PROTOCOLS.has(new URL(value, document.baseURI).protocol);
        } catch {
            // An unparseable URL is not something we want to hand to an iframe either.
            return false;
        }
    }
}
