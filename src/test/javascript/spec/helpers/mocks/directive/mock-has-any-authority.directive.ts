import { Directive, OnInit, TemplateRef, ViewContainerRef, input } from '@angular/core';
import { Authority } from 'app/foundation/constants/authority.constants';

@Directive({
    selector: '[jhiHasAnyAuthority]',
    exportAs: 'jhiHasAnyAuthority',
})
export class MockHasAnyAuthorityDirective implements OnInit {
    // Receives the [jhiHasAnyAuthority] binding; the mock always renders the content regardless of value.
    jhiHasAnyAuthority = input<string | string[] | readonly Authority[]>();

    constructor(
        private templateRef: TemplateRef<any>,
        private viewContainerRef: ViewContainerRef,
    ) {}

    ngOnInit(): void {
        this.viewContainerRef.createEmbeddedView(this.templateRef);
    }
}
