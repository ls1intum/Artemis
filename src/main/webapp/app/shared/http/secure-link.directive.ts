import { Directive, ElementRef } from '@angular/core';

/**
 * Avoid Reverse Tabnabbing vulnerability: https://www.owasp.org/index.php/Reverse_Tabnabbing.
 */
@Directive({
    selector: '[jhiSecureLink]',
})
export class SecureLinkDirective {
    constructor(el: ElementRef) {
        el.nativeElement.target = '_blank';
        el.nativeElement.rel = 'noopener noreferrer';
    }
}
