import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({
    name: 'safeUrl',
    standalone: false,
})
export class SafeUrlPipe implements PipeTransform {
    constructor(private sanitizer: DomSanitizer) {}

    /**
     * Bypasses the security checks for a specified URL.
     * @param value The URL that is considered safe.
     */
    transform(value: any) {
        return this.sanitizer.bypassSecurityTrustUrl(value);
    }
}
