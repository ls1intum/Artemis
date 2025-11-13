import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { PasskeyContentComponent } from 'app/core/auth/passkey-content/passkey-content.component';

@Component({
    selector: 'jhi-passkey-required',
    imports: [PasskeyContentComponent],
    templateUrl: './passkey-required.component.html',
})
export class PasskeyRequiredComponent implements OnInit {
    protected readonly ButtonType = ButtonType;
    protected readonly faLock = faLock;
    protected readonly faKey = faKey;

    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    protected readonly accountService = inject(AccountService);

    userHasRegisteredAPasskey: boolean = false;
    returnUrl: string | undefined = undefined;

    ngOnInit() {
        this.initializeUserIdentity().then((response) => {});

        this.route.queryParams.subscribe((params) => {
            this.returnUrl = params['returnUrl'] || '/';
        });
    }

    /**
     * Ensures the user identity is loaded from the server (important for page reloads)
     */
    private async initializeUserIdentity() {
        await this.accountService.identity();

        this.userHasRegisteredAPasskey = !this.accountService.userIdentity()?.askToSetupPasskey;

        const redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey = this.accountService.isUserLoggedInWithApprovedPasskey() && this.returnUrl;
        if (redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey) {
            this.router.navigateByUrl(this.returnUrl!);
        }
    }

    redirectToOriginalUrlOrHome() {
        if (this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
        } else {
            this.router.navigate(['/']);
        }
    }
}
