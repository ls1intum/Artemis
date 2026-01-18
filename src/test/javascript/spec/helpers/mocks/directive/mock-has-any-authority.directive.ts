import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';

@Directive({
    selector: '[jhiHasAnyAuthority]',
    exportAs: 'jhiHasAnyAuthority',
})
export class MockHasAnyAuthorityDirective {
    constructor(
        private templateRef: TemplateRef<any>,
        private viewContainerRef: ViewContainerRef,
    ) {}

    @Input()
    set jhiHasAnyAuthority(_value: string | string[] | readonly Authority[]) {
        this.updateView();
    }

    private updateView(): void {
        this.viewContainerRef.createEmbeddedView(this.templateRef);
    }
}
