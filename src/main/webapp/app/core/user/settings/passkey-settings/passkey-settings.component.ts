import { Component, OnDestroy, effect, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faKey, faPencil, faPlus, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { Observable, Subject, Subscription, of, tap } from 'rxjs';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';
import { PasskeySettingsApiService } from 'app/core/user/settings/passkey-settings/passkey-settings-api.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ActionType, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { getOS } from 'app/shared/util/os-detector.util';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CustomMaxLengthDirective } from 'app/shared/validators/custom-max-length-validator/custom-max-length-validator.directive';
import cloneDeep from 'lodash';

const InvalidStateError = {
    name: 'InvalidStateError',
    authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode: 11,
};

const UserAbortedPasskeyCreationError = {
    code: 0,
    name: 'NotAllowedError',
};

export interface DisplayedPasskey extends PasskeyDTO {
    isEditingLabel?: boolean;
    labelBeforeEdit?: string;
}

@Component({
    selector: 'jhi-passkey-settings',
    imports: [TranslateDirective, FaIconComponent, DeleteButtonDirective, ArtemisDatePipe, ButtonComponent, CommonModule, FormsModule, CustomMaxLengthDirective],
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
    protected readonly faPencil = faPencil;
    protected readonly faBan = faBan;
    protected readonly faTimes = faTimes;
    protected readonly faKey = faKey;
    protected readonly MAX_PASSKEY_LABEL_LENGTH = 64;

    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private webauthnApiService = inject(WebauthnApiService);
    private passkeySettingsApiService = inject(PasskeySettingsApiService);

    private dialogErrorSource = new Subject<string>();

    registeredPasskeys = signal<DisplayedPasskey[]>([]);

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

    async updateRegisteredPasskeys(): Promise<void> {
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

            console.log("Creating credential options")

            const credentialOptions = this.createCredentialOptions(options, user);

            console.log("Successfully created credential options")

            const authenticatorCredential = await navigator.credentials.create({
                publicKey: credentialOptions,
            });

            const credential = this.getCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential)

            console.log(credential)
            console.log(JSON.stringify(credential))

            await this.webauthnApiService.registerPasskey({
                publicKey: {
                    credential: credential,
                    label: `${user.email} - ${getOS()}`,
                },
            });
        } catch (error) {
            console.log(error)

            if (error.name == UserAbortedPasskeyCreationError.name && error.code == UserAbortedPasskeyCreationError.code) {
                return; // the user pressed cancel in the passkey creation dialog
            }

            if (error.name == InvalidStateError.name && error.code == InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.registration');
            }
            return;
        }
        await this.updateRegisteredPasskeys();
    }

    getCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null) {
        try {
            // properly returned credentials can be stringified
            JSON.stringify(credential);
            return credential;
        } catch (error) {
            // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
            console.log("Credential is not serializable, using a workaround to get the credential")

            console.log("credential before fix")
            console.log(credential)


            const fixedCredential = this.fixClonedCredential(cloneDeep(credential) as unknown as Credential);


            console.log("fixed credential")
            console.log(fixedCredential)
            console.log(JSON.stringify(fixedCredential));

            return fixedCredential;
        }
    }

    convertToArrayBuffer(rawIdObject: Record<string, number> | null | undefined): ArrayBuffer {
        if (!rawIdObject || typeof rawIdObject !== 'object') {
            throw new TypeError('Invalid input: rawIdObject must be a non-null object');
        }

        const uint8Array = new Uint8Array(Object.values(rawIdObject));
        return uint8Array.buffer;
    }

    arrayBufferToBase64(buffer: ArrayBuffer): string {
        const uint8Array = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < uint8Array.length; i++) {
            binary += String.fromCharCode(uint8Array[i]);
        }
        return btoa(binary);
    }

    fixClonedCredential(clonedCredential: any): any {
        const serializedCredential = JSON.stringify(clonedCredential);
        const credential = JSON.parse(serializedCredential);

        const rawIdAsArrayBuffer = this.convertToArrayBuffer(credential.rawId);
        const clientDataJSONAsArrayBuffer = this.convertToArrayBuffer(credential.response.clientDataJSON);
        const attestationObjectAsArrayBuffer = this.convertToArrayBuffer(credential.response.attestationObject);

        return {
            authenticatorAttachment: credential.authenticatorAttachment,
            // clientExtensionResults: "",
            id: credential.id,
            rawId: this.arrayBufferToBase64(rawIdAsArrayBuffer),
            response: {
                attestationObject: this.arrayBufferToBase64(attestationObjectAsArrayBuffer),
                // authenticatorData: "",
                clientDataJSON: this.arrayBufferToBase64(clientDataJSONAsArrayBuffer),
                // publicKey: "",
                // publicKeyAlgorithm: "",
                // transports: ""
            },
            type: credential.type
        };
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

    private createCredentialOptions(options: PublicKeyCredentialCreationOptions, user: User): PublicKeyCredentialCreationOptions {
        const username = user.email;

        if (!user.id || !username) {
            throw new Error('Invalid credential');
        }

        return {
            ...options,
            challenge: decodeBase64url(options.challenge),
            user: {
                id: new TextEncoder().encode(user.id.toString()),
                name: username,
                displayName: username,
            },
            excludeCredentials: options.excludeCredentials?.map((credential) => ({
                ...credential,
                id: decodeBase64url(credential.id),
            })),
            authenticatorSelection: {
                requireResidentKey: true,
                userVerification: 'discouraged', // a little less secure than 'preferred' or 'required', but more user-friendly
            },
        };
    }

    getDeleteSummary(passkey: PasskeyDTO | undefined): Observable<EntitySummary> | undefined {
        if (!passkey) {
            return undefined;
        }

        const summary: EntitySummary = {
            'artemisApp.userSettings.passkeySettingsPage.label': passkey.label,
            'artemisApp.userSettings.passkeySettingsPage.created': passkey.created,
            'artemisApp.userSettings.passkeySettingsPage.lastUsed': passkey.lastUsed,
        };

        return of(summary);
    }

    editPasskeyLabel(passkey: DisplayedPasskey) {
        passkey.labelBeforeEdit = passkey.label ?? '';
        passkey.isEditingLabel = true;
    }

    async savePasskeyLabel(passkey: DisplayedPasskey) {
        passkey.isEditingLabel = false;

        try {
            passkey = await this.passkeySettingsApiService.updatePasskeyLabel(passkey.credentialId, passkey);
        } catch (error) {
            this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.save');
            passkey.label = passkey.labelBeforeEdit ?? '';
        }
    }

    cancelEditPasskeyLabel(passkey: DisplayedPasskey) {
        passkey.isEditingLabel = false;
        passkey.label = passkey.labelBeforeEdit ?? '';
    }

    async deletePasskey(passkey: PasskeyDTO) {
        this.isDeletingPasskey = true;
        try {
            await this.passkeySettingsApiService.deletePasskey(passkey.credentialId);
            await this.updateRegisteredPasskeys();
        } catch (error) {
            this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.delete');
        }
        this.isDeletingPasskey = false;
        this.dialogErrorSource.next('');
    }
}
