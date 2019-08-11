import { Directive, ElementRef } from '@angular/core';
import { JavaBridgeService } from 'app/core/intellij/java-bridge.service';

@Directive({
    selector: '[jhiIntellij]',
})
export class IntellijDirective {
    constructor(el: ElementRef, javaBridge: JavaBridgeService) {
        if (javaBridge.isIntelliJ()) {
            el.nativeElement.style.display = 'none';
        }
    }
}
