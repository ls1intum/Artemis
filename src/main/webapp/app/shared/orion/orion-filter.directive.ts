import { Directive, ElementRef, Input, OnInit, inject } from '@angular/core';
import { isOrion } from 'app/shared/orion/orion';

@Directive({
    selector: '[jhiOrionFilter]',
})
export class OrionFilterDirective implements OnInit {
    private el = inject(ElementRef);

    @Input() showInOrionWindow: boolean;

    ngOnInit(): void {
        if ((!this.showInOrionWindow && isOrion) || (this.showInOrionWindow && !isOrion)) {
            this.el.nativeElement.style.display = 'none';
        }
    }
}
