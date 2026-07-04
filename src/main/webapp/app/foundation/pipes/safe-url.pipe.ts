import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({ name: 'safeUrl' })
export class SafeUrlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Bypasses the security checks for a specified URL.
     * @param value The URL that is considered safe. May be nullish (e.g. when the source URL could not be built),
     *              in which case it is passed through to the sanitizer unchanged to preserve prior behavior.
     */
    transform(value: string | undefined | null) {
        return this.sanitizer.bypassSecurityTrustUrl(value as string);
    }
}
