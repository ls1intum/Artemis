import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { faBolt, faFingerprint, faKey, faLock, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { WebauthnService } from 'app/account/user/settings/passkey-settings/webauthn.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';

export const EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY = 'earliestSetupPasskeyReminderDate';

@Component({
    selector: 'jhi-setup-passkey-modal',
    imports: [FormsModule, TranslateDirective, FontAwesomeModule, TumUiDialogComponent, TumUiButtonComponent],
    templateUrl: './setup-passkey-modal.component.html',
})
export class SetupPasskeyModalComponent implements OnInit {
    protected readonly faKey = faKey;
    protected readonly faShieldHalved = faShieldHalved;
    protected readonly faFingerprint = faFingerprint;
    protected readonly faBolt = faBolt;
    protected readonly faLock = faLock;

    readonly visible = signal(false);

    /**
     * Set once the user dismisses the prompt during the current session (e.g. via "Set up later").
     * The modal is a singleton in the app shell, so this prevents it from reopening when the
     * authentication state re-emits (e.g. after changing the AI experience). Reset on a full reload.
     */
    private dismissedForCurrentSession = false;

    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);
    private readonly accountService = inject(AccountService);
    private readonly localStorageService = inject(LocalStorageService);
    private readonly profileService = inject(ProfileService);
    private readonly destroyRef = inject(DestroyRef);

    ngOnInit(): void {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY)) {
            return;
        }

        this.accountService
            .getAuthenticationState()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((user) => {
                if (user) {
                    this.openIfNeeded();
                }
            });
    }

    /**
     * <p>
     * We want users to use passkey authentication over password authentication.
     * </p>
     * <p>
     * If the passkey feature is enabled and no passkeys are set up yet, we display a modal that informs the user about passkeys and forwards to the setup page.
     * </p>
     */
    private openIfNeeded(): void {
        if (this.dismissedForCurrentSession) {
            return;
        }

        const earliestReminderDate = this.localStorageService.retrieveDate(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY);
        const userDisabledReminderForCurrentTimeframe = earliestReminderDate && new Date() < earliestReminderDate;
        if (userDisabledReminderForCurrentTimeframe) {
            return;
        }

        if (!this.accountService.userIdentity()?.askToSetupPasskey) {
            return;
        }

        this.visible.set(true);
    }

    async setupPasskey() {
        await this.webauthnService.addNewPasskey(this.accountService.userIdentity());
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
        this.closeModal();
    }

    remindMeIn30Days() {
        const currentDate = new Date();
        const futureDate = new Date(currentDate.setDate(currentDate.getDate() + 30));
        this.localStorageService.store<Date>(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, futureDate);
        this.closeModal();
    }

    closeModal(): void {
        this.dismissedForCurrentSession = true;
        this.visible.set(false);
    }
}
