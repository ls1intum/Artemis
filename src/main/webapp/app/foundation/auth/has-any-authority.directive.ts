import { DestroyRef, Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/foundation/constants/authority.constants';

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
    private readonly destroyRef = inject(DestroyRef);

    readonly jhiHasAnyAuthority = input.required<string | string[] | readonly Authority[]>();

    private authorities: readonly Authority[];

    constructor() {
        // Re-derive the authorities and re-render whenever the input changes.
        effect(() => {
            const value = this.jhiHasAnyAuthority();
            this.authorities = typeof value === 'string' ? [value as Authority] : (value as readonly Authority[]);
            this.updateView();
        });
        // Get notified each time authentication state changes. Subscribe exactly once so the subscription is not
        // re-created on every input change (the authentication state observable never completes).
        this.accountService
            .getAuthenticationState()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.updateView());
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
