import { Component, inject, input, output, viewChild } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LoginWithPasskeyModalComponent } from 'app/core/navbar/server-administration/login-with-passkey-modal/login-with-passkey-modal.component';
import { AccountService } from 'app/core/auth/account.service';
import { PasskeyAuthenticationGuard } from 'app/core/auth/passkey-authentication-guard/passkey-authentication.guard';
import { faUserShield } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-server-administration',
    imports: [TranslateDirective, FaIconComponent, HasAnyAuthorityDirective, RouterLinkActive, RouterLink, LoginWithPasskeyModalComponent],
    templateUrl: './server-administration.component.html',
    styleUrl: '../navbar.scss',
})
export class ServerAdministrationComponent {
    protected readonly faUserShield = faUserShield;

    private readonly accountService = inject(AccountService);
    private readonly isLoggedInWithPasskeyGuard = inject(PasskeyAuthenticationGuard);

    loginWithPasskeyModal = viewChild.required<LoginWithPasskeyModalComponent>(LoginWithPasskeyModalComponent);

    isExamActive = input<boolean>(false);
    isExamStarted = input<boolean>(false);

    collapseNavbarListener = output<void>();

    collapseNavbar() {
        this.collapseNavbarListener.emit();
    }

    protected showModalForPasskeyLogin(): boolean {
        if (!this.isLoggedInWithPasskeyGuard.shouldEnforcePasskeyForAdminFeatures()) {
            return false;
        }
        if (this.accountService.isUserLoggedInWithApprovedPasskey()) {
            return false;
        }

        this.loginWithPasskeyModal().showModal = true;
        return true;
    }

    onLinkClick(event: Event) {
        if (this.showModalForPasskeyLogin()) {
            event.preventDefault();
        } else {
            this.collapseNavbar();
        }
    }
}
