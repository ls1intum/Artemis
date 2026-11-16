import { DomSanitizer } from '@angular/platform-browser';

export function render(sanitizer: DomSanitizer, html: string) {
    // ruleid: artemis-bypass-security-trust
    return sanitizer.bypassSecurityTrustHtml(html);
}

export function renderSanitized(sanitizer: DomSanitizer, html: string) {
    // ok: artemis-bypass-security-trust
    return sanitizer.sanitize(1, html);
}
