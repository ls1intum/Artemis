import { Component, OnDestroy, effect, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { Observable, Subject, Subscription, of, tap } from 'rxjs';
import { PasskeyOptions } from 'app/core/user/settings/passkey-settings/entities/passkey-options.model';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/utils';
import { PasskeyDto } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';
import { PasskeySettingsApiService } from 'app/core/user/settings/passkey-settings/passkey-settings-api.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ActionType, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { getOS } from 'app/shared/util/os-detector.util';
import { TranslateService } from '@ngx-translate/core';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button/button.component';

const InvalidStateError = {
    name: 'InvalidStateError',
    authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode: 11,
};

@Component({
    selector: 'jhi-passkey-settings',
    imports: [TranslateDirective, FaIconComponent, DeleteButtonDirective, ArtemisDatePipe, ButtonComponent],
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
    private passkeySettingsApiService = inject(PasskeySettingsApiService);
    private translateService = inject(TranslateService);

    private dialogErrorSource = new Subject<string>();

    registeredPasskeys = signal<PasskeyDto[]>([]);

    dialogError$ = this.dialogErrorSource.asObservable();

    currentUser = signal<User | undefined>(undefined);

    deleteMessage = '';
    isDeletingPasskey = false;

    private authStateSubscription: Subscription;

    constructor() {
        this.loadCurrentUser();

        effect(() => {
            this.loadPasskeysWhenUserDetailsChange().then();
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

            await this.webauthnApiService.registerPasskey({
                publicKey: {
                    credential: credential,
                    label: `${user.email} - ${getOS()}`,
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

    getDeleteMessage(passkey: PasskeyDto) {
        return this.translateService.instant('artemisApp.userSettings.passkeySettingsPage.deletePasskeyQuestion', { passkeyLabel: passkey.label });
    }

    getDeleteSummary(passkey: PasskeyDto | undefined): Observable<EntitySummary> | undefined {
        if (!passkey) {
            return undefined; // Explicitly return undefined if passkey is not provided
        }

        const summary: EntitySummary = {
            'artemisApp.userSettings.passkeySettingsPage.label': passkey.label,
            'artemisApp.userSettings.passkeySettingsPage.created': passkey.created,
            'artemisApp.userSettings.passkeySettingsPage.lastUsed': passkey.lastUsed,
        };

        return of(summary); // Return an Observable<EntitySummary> if passkey is valid
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
}
