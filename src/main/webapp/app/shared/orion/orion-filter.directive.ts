import { Directive, ElementRef, Input, OnInit } from '@angular/core';
import { isOrion } from 'app/shared/orion/orion';

@Directive({
    selector: '[jhiOrionFilter]',
})
export class OrionFilterDirective implements OnInit {
    @Input() showInOrionWindow: boolean;

    constructor(private el: ElementRef) {}

    /**
     * Hide element with attribute 'jhiOrionFilter', if user agent is not 'Orion'/'IntelliJ' and parent component
     * wants to show the element or if the opposite holds.
     */
    ngOnInit(): void {
        if ((!this.showInOrionWindow && isOrion) || (this.showInOrionWindow && !isOrion)) {
            this.el.nativeElement.style.display = 'none';
        }
    }
}
