import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';

/**
 * Bridges a browser login back to an external client (e.g. the VS Code extension).
 *
 * The external client opens this guarded page with a PKCE {@code code_challenge}, a custom-scheme
 * {@code callback} and an opaque {@code state}. After the user has authenticated in the browser by any
 * method (passkey, SAML2 SSO, password), this page exchanges the challenge for a one-time code via
 * {@link AccountService#issueExternalLoginCode} and redirects the browser to
 * {@code callback?code=<code>&state=<state>}, handing control back to the client.
 *
 * The server validates the callback when minting the code, so a rejected callback never produces a redirect.
 */
@Component({
    selector: 'jhi-external-login',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, RouterLink],
    templateUrl: './external-login.component.html',
})
export class ExternalLoginComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly accountService = inject(AccountService);
    private readonly alertService = inject(AlertService);

    protected readonly status = signal<'redirecting' | 'error'>('redirecting');
    protected readonly deepLink = signal<string | undefined>(undefined);

    ngOnInit(): void {
        const params = this.route.snapshot.queryParamMap;
        const codeChallenge = params.get('code_challenge');
        const callback = params.get('callback');
        const state = params.get('state');

        if (!codeChallenge || !callback || !state) {
            this.fail('artemisApp.externalLogin.error.missingParams');
            return;
        }

        this.accountService.issueExternalLoginCode({ codeChallenge, callback }).subscribe({
            next: ({ code }) => this.redirectToCallback(callback, code, state),
            error: () => this.fail('artemisApp.externalLogin.error.codeIssuanceFailed'),
        });
    }

    /**
     * Redirects the browser to the (server-validated) callback with the one-time code and state appended as
     * canonical query parameters, overwriting any pre-existing {@code code}/{@code state} so the external
     * client can never receive ambiguous or duplicated security-relevant parameters.
     */
    private redirectToCallback(callback: string, code: string, state: string): void {
        let target: URL;
        try {
            target = new URL(callback);
        } catch {
            this.fail('artemisApp.externalLogin.error.codeIssuanceFailed');
            return;
        }
        target.searchParams.set('code', code);
        target.searchParams.set('state', state);
        const url = target.toString();
        this.deepLink.set(url);
        this.redirect(url);
    }

    protected openManually(): void {
        const target = this.deepLink();
        if (target) {
            this.redirect(target);
        }
    }

    private fail(translationKey: string): void {
        this.status.set('error');
        this.alertService.error(translationKey);
    }

    /** Navigates the browser to the (custom-scheme) callback. Kept as a seam so tests can spy on it. */
    protected redirect(url: string): void {
        window.location.href = url;
    }
}
