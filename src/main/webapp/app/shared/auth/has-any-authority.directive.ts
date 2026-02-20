import { Directive, Input, TemplateRef, ViewContainerRef, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/shared/constants/authority.constants';

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
@Directive({ selector: '[jhiHasAnyAuthority]' })
export class HasAnyAuthorityDirective {
    private accountService = inject(AccountService);
    private templateRef = inject<TemplateRef<any>>(TemplateRef);
    private viewContainerRef = inject(ViewContainerRef);

    private authorities: readonly Authority[];

    @Input()
    set jhiHasAnyAuthority(value: string | string[] | readonly Authority[]) {
        this.authorities = typeof value === 'string' ? [value as Authority] : (value as readonly Authority[]);
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
