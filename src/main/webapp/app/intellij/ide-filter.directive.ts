import { Directive, Input, ElementRef, OnInit } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

@Directive({
    selector: '[jhi-ide-filter]',
})
export class IdeFilterDirective implements OnInit {
    @Input() showInIDE: boolean;

    constructor(private el: ElementRef, private javaBridge: JavaBridgeService) {}

    ngOnInit(): void {
        if ((!this.showInIDE && this.javaBridge.isIntelliJ()) || (this.showInIDE && !this.javaBridge.isIntelliJ())) {
            this.el.nativeElement.style.display = 'none';
        }
    }
}
