import { Pipe, PipeTransform, SecurityContext, inject } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

@Pipe({ name: 'safeUrl' })
export class SafeUrlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Sanitizes the given URL and marks the result as trusted so Angular renders it in a link.
     *
     * Sanitizing inside the pipe makes it safe regardless of the caller, the same reasoning
     * {@link SafeHtmlPipe} follows: a caller that passes user-controlled data can no longer introduce
     * a `javascript:` URL through this pipe. Angular's URL sanitizer rejects only the script-bearing
     * schemes and leaves application schemes such as `vscode://` and `sourcetree://` untouched, which
     * is what the IDE deep links in the code button rely on.
     *
     * @param value The URL to sanitize and render. May be nullish (e.g. when the source URL could not
     *                  be built), in which case an empty URL is returned.
     */
    transform(value: string | undefined | null): SafeUrl {
        const sanitized = this.sanitizer.sanitize(SecurityContext.URL, value ?? null);
        // Angular rewrites a rejected URL to an inert `unsafe:` form rather than dropping it. Drop it here: trusting
        // that string would render a visibly broken link, and it makes the rejection something a test can assert
        // exactly instead of only matching against.
        const rejected = sanitized === null || sanitized.startsWith('unsafe:');
        return this.sanitizer.bypassSecurityTrustUrl(rejected ? '' : sanitized);
    }
}
