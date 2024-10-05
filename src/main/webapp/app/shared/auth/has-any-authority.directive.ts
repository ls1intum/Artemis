import { Directive, Input, TemplateRef, ViewContainerRef, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';

/**
 * @whatItDoes Conditionally includes an HTML element if current user has any
 * of the authorities in any course passed as the `expression`.
 *
 * @howToUse
 * ```
 *     <some-element *jhiHasAnyAuthority="Authority.ADMIN">...</some-element>
 *
 *     <some-element *jhiHasAnyAuthority="[Authority.ADMIN, Authority.USER]">...</some-element>
 * ```
 */
@Directive({
    selector: '[jhiHasAnyAuthority]',
})
export class HasAnyAuthorityDirective {
    private accountService = inject(AccountService);
    private templateRef = inject<TemplateRef<any>>(TemplateRef);
    private viewContainerRef = inject(ViewContainerRef);

    private authorities: string[];

    @Input()
    set jhiHasAnyAuthority(value: string | string[]) {
        this.authorities = typeof value === 'string' ? [<string>value] : <string[]>value;
        this.updateView();
        // Get notified each time authentication state changes.
        this.accountService.getAuthenticationState().subscribe(() => this.updateView());
    }

    private updateView(): void {
        this.accountService.hasAnyAuthority(this.authorities).then((result) => {
            this.viewContainerRef.clear();
            if (result) {
                this.viewContainerRef.createEmbeddedView(this.templateRef);
            }
        });
    }
}
