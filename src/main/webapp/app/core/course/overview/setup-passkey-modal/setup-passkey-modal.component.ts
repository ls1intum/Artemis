import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { Router } from '@angular/router';
import { addNewPasskey } from 'app/core/user/settings/passkey-settings/util/credential.util';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';

export const EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY = 'earliestSetupPasskeyReminderDate';

@Component({
    selector: 'jhi-setup-passkey-modal',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FontAwesomeModule],
    templateUrl: './setup-passkey-modal.component.html',
})
export class SetupPasskeyModalComponent {
    protected readonly faKey = faKey;
    protected readonly faShieldHalved = faShieldHalved;

    private activeModal = inject(NgbActiveModal);
    private router = inject(Router);
    private webauthnApiService = inject(WebauthnApiService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);

    navigateToSetupPasskey() {
        this.closeModal();
        this.router.navigateByUrl('/user-settings/passkeys');
    }

    async setupPasskey() {
        await addNewPasskey(this.accountService.userIdentity, this.webauthnApiService, this.alertService);
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
        this.closeModal();
    }

    remindMeIn30Days() {
        const currentDate = new Date();
        const futureDate = new Date(currentDate.setDate(currentDate.getDate() + 30));
        localStorage.setItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, futureDate.toISOString());
        this.closeModal();
    }

    closeModal(): void {
        this.activeModal.close();
    }
}
