import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { PasskeyPromptComponent } from 'app/core/auth/passkey-prompt/passkey-prompt.component';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-passkey-required',
    imports: [PasskeyPromptComponent],
    templateUrl: './passkey-authentication-page.component.html',
})
export class PasskeyAuthenticationPageComponent implements OnInit, OnDestroy {
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    protected readonly accountService = inject(AccountService);

    userHasRegisteredPasskey: boolean = false;
    returnUrl: string | undefined = undefined;

    private routeSubscription?: Subscription;

    ngOnInit() {
        this.userHasRegisteredPasskey = !this.accountService.userIdentity()?.askToSetupPasskey;

        const redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey = this.accountService.isUserLoggedInWithApprovedPasskey() && this.returnUrl;
        if (redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey) {
            this.router.navigateByUrl(this.returnUrl!);
        }

        this.routeSubscription = this.route.queryParams.subscribe((params) => {
            this.returnUrl = params['returnUrl'] || '/';
        });
    }

    ngOnDestroy() {
        this.routeSubscription?.unsubscribe();
    }

    redirectToOriginalUrlOrHome() {
        if (this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
        } else {
            this.router.navigate(['/']);
        }
    }
}
