import {Pipe, PipeTransform} from '@angular/core';
import {DomSanitizer} from '@angular/platform-browser';

@Pipe({name: 'safeUrl'})
export class SafeUrlPipe implements PipeTransform {
    constructor( private sanitizer: DomSanitizer ) { }
    transform(value) {
        return this.sanitizer.bypassSecurityTrustUrl(value);
    }
}
