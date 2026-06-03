import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
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
        // Re-render whenever the required authorities or the authentication state change: hasAnyAuthorityDirect()
        // synchronously reads the userIdentity()/authenticated() signals, so this effect tracks them and re-runs on
        // login/logout — no separate auth-state subscription is needed. The effect runs after inputs are set, so
        // reading the required input here is safe (a constructor subscription would read it during construction and
        // throw NG0950). Toggling the embedded view via ViewContainerRef notifies the change-detection scheduler on
        // attach/detach, so this renders correctly under zoneless change detection.
        effect(() => {
            const authorized = this.accountService.hasAnyAuthorityDirect(this.jhiHasAnyAuthority());
            this.viewContainerRef.clear();
            if (authorized) {
                this.viewContainerRef.createEmbeddedView(this.templateRef);
            }
        });
    }
}
