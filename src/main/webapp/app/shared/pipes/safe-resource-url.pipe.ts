import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({ name: 'safeResourceUrl' })
export class SafeResourceUrlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Pipe that can be used to bypass security for a resource url, e.g. for `<script src>`, or `<iframe src>`.
     * @param value The resource to bypass security check.
     */
    transform(value: any) {
        return this.sanitizer.bypassSecurityTrustResourceUrl(value);
    }
}
