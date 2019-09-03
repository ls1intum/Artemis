import { Directive, ElementRef, Input, OnInit } from '@angular/core';
import { isIntelliJ } from 'app/intellij/intellij';

@Directive({
    selector: '[jhiIdeFilter]',
})
export class IdeFilterDirective implements OnInit {
    @Input() showInIDE: boolean;

    constructor(private el: ElementRef) {}

    ngOnInit(): void {
        if ((!this.showInIDE && isIntelliJ) || (this.showInIDE && !isIntelliJ)) {
            this.el.nativeElement.style.display = 'none';
        }
    }
}
