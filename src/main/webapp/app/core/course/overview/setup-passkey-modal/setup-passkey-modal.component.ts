import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { DialogModule } from 'primeng/dialog';

export const EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY = 'earliestSetupPasskeyReminderDate';

@Component({
    selector: 'jhi-setup-passkey-modal',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FontAwesomeModule, DialogModule],
    templateUrl: './setup-passkey-modal.component.html',
})
export class SetupPasskeyModalComponent implements OnInit {
    protected readonly faKey = faKey;
    protected readonly faShieldHalved = faShieldHalved;

    readonly visible = signal(false);

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

    private openIfNeeded(): void {
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
        this.visible.set(false);
    }
}
