import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
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

    /** Required authorities; a single authority string is normalized to an array. */
    readonly jhiHasAnyAuthority = input.required<readonly Authority[], string | string[] | readonly Authority[]>({
        transform: (value) => (typeof value === 'string' ? [value as Authority] : (value as readonly Authority[])),
    });

    constructor() {
        // Re-evaluate the view whenever the required authorities change.
        effect(() => {
            this.jhiHasAnyAuthority();
            this.updateView();
        });
        // Get notified each time authentication state changes (subscribed once, not per input change).
        this.accountService
            .getAuthenticationState()
            .pipe(takeUntilDestroyed())
            .subscribe(() => this.updateView());
    }

    private updateView(): void {
        this.accountService.hasAnyAuthority(this.jhiHasAnyAuthority()).then((result) => {
            this.viewContainerRef.clear();
            if (result) {
                this.viewContainerRef.createEmbeddedView(this.templateRef);
            }
        });
    }
}
