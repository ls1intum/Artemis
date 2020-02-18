import { Directive, ElementRef, Input, OnInit } from '@angular/core';
import { isOrion } from 'app/orion/orion';

@Directive({
    selector: '[jhiOrionFilter]',
})
export class OrionFilterDirective implements OnInit {
    @Input() showInOrionWindow: boolean;

    constructor(private el: ElementRef) {}

    ngOnInit(): void {
        if ((!this.showInOrionWindow && isOrion) || (this.showInOrionWindow && !isOrion)) {
            this.el.nativeElement.style.display = 'none';
        }
    }
}
