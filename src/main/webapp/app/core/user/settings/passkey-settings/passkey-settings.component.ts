import { Component, OnDestroy, effect, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { Subject, Subscription, tap } from 'rxjs';
import { PasskeyOptions } from 'app/core/user/settings/passkey-settings/entities/passkey-options.model';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/utils';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { PasskeyDto } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';
import { PasskeySettingsApiService } from 'app/core/user/settings/passkey-settings/passkey-settings-api.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';

const InvalidStateError = {
    name: 'InvalidStateError',
    authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode: 11,
};

@Component({
    selector: 'jhi-passkey-settings',
    imports: [TranslateDirective, DeleteButtonDirective, FaIconComponent, ArtemisDatePipe, ButtonComponent],
    templateUrl: './passkey-settings.component.html',
    styleUrl: './passkey-settings.component.scss',
})
export class PasskeySettingsComponent implements OnDestroy {
    protected readonly ActionType = ActionType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly faPlus = faPlus;
    protected readonly faSave = faSave;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;

    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private webauthnApiService = inject(WebauthnApiService);
    private webauthnService = inject(WebauthnService);
    private passkeySettingsApiService = inject(PasskeySettingsApiService);

    private dialogErrorSource = new Subject<string>();

    registeredPasskeys = signal<PasskeyDto[]>([]);

    dialogError$ = this.dialogErrorSource.asObservable();

    currentUser = signal<User | undefined>(undefined);

    isDeletingPasskey = false;

    private authStateSubscription: Subscription;

    constructor() {
        this.loadCurrentUser();

        effect(() => {
            this.loadPasskeysWhenUserDetailsChange();
        });
    }

    ngOnDestroy(): void {
        this.authStateSubscription.unsubscribe();
    }

    private async updateRegisteredPasskeys(): Promise<void> {
        this.registeredPasskeys.set(await this.passkeySettingsApiService.getRegisteredPasskeys());
    }

    async addNewPasskey() {
        try {
            const user = this.currentUser();
            if (!user) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new Error('User or Username is not defined');
            }
            const options = await this.webauthnApiService.getRegistrationOptions();
            const credentialOptions = this.createCredentialOptions(options, user);

            const credential = await navigator.credentials.create({
                publicKey: credentialOptions,
            });

            if (!credential) {
                // TODO check if server fails here anyways
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new Error('Invalid credential');
            }

            await this.webauthnApiService.registerPasskey({
                publicKey: {
                    credential: credential,
                    label: user.email ?? user.id?.toString() ?? 'Artemis Passkey',
                },
            });
        } catch (error) {
            if (error.name == InvalidStateError.name && error.code == InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.registration');
            }
        }
        await this.updateRegisteredPasskeys();
    }

    async loginWithPublicKeyCredential() {
        const credential = await this.webauthnService.getCredential();

        if (!credential || credential.type != 'public-key') {
            alert("Credential is undefined or type is not 'public-key'");
            return;
        }

        await this.webauthnApiService.loginWithPasskey(credential);
    }

    private loadCurrentUser() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser.set(user);
                    return this.currentUser;
                }),
            )
            .subscribe();
    }

    private async loadPasskeysWhenUserDetailsChange() {
        if (this.currentUser != undefined) {
            await this.updateRegisteredPasskeys();
        }
    }

    private createCredentialOptions(options: PasskeyOptions, user: User): PublicKeyCredentialCreationOptions {
        const username = user.email;

        if (!user.id || !username) {
            throw new Error('Invalid credential');
        }

        // TODO verify values are set properly
        return {
            ...options,
            challenge: decodeBase64url(options.challenge),
            user: {
                id: new TextEncoder().encode(user.id.toString()),
                name: username,
                displayName: username,
            },
            excludeCredentials: options.excludeCredentials.map((credential) => ({
                ...credential,
                id: decodeBase64url(credential.id),
            })),
            authenticatorSelection: {
                requireResidentKey: true,
                userVerification: 'discouraged', // a little less secure than 'preferred' or 'required', but more user-friendly
            },
        };
    }

    async deletePasskey(passkey: PasskeyDto) {
        this.isDeletingPasskey = true;
        try {
            await this.passkeySettingsApiService.deletePasskey(passkey.credentialId);
            await this.updateRegisteredPasskeys();
        } catch (error) {
            this.alertService.addErrorAlert('Unable to delete passkey');
        }
        this.isDeletingPasskey = false;
        this.dialogErrorSource.next('');
    }

    editPasskey() {
        // TODO
        this.alertService.addErrorAlert('Not implemented yet');
    }
}
