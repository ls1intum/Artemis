import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({ name: 'safeUrl' })
export class SafeUrlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Bypasses the security checks for a specified URL.
     * @param value The URL that is considered safe.
     */
    transform(value: any) {
        return this.sanitizer.bypassSecurityTrustUrl(value);
    }
}
