import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({ name: 'safeResourceUrl' })
export class SafeResourceUrlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Pipe that can be used to bypass security for a resource url, e.g. for `<script src>`, or `<iframe src>`.
     * @param value The resource to bypass security check. May be nullish (e.g. when no video source is available),
     *              in which case it is passed through to the sanitizer unchanged to preserve prior behavior.
     */
    transform(value: string | undefined | null) {
        return this.sanitizer.bypassSecurityTrustResourceUrl(value as string);
    }
}
