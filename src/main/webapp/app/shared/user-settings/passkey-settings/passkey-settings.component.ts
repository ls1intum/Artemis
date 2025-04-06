import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faEdit, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { Subject, Subscription, tap } from 'rxjs';
import { PasskeyOptions } from 'app/shared/user-settings/passkey-settings/entities/passkey-options.model';
import { WebauthnApiService } from 'app/shared/user-settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/utils';
import { WebauthnService } from 'app/shared/user-settings/passkey-settings/webauthn.service';
import { PasskeyDto } from 'app/shared/user-settings/passkey-settings/dto/passkey.dto';
import { PasskeySettingsApiService } from 'app/shared/user-settings/passkey-settings/passkey-settings-api.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';

@Component({
    selector: 'jhi-passkey-settings',
    imports: [TranslateDirective, ButtonComponent, DeleteButtonDirective, FaIconComponent, ArtemisDatePipe],
    templateUrl: './passkey-settings.component.html',
    styleUrl: './passkey-settings.component.scss',
})
export class PasskeySettingsComponent implements OnInit, OnDestroy {
    protected readonly ActionType = ActionType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly faEdit = faEdit;
    protected readonly faSave = faSave;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;

    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private webauthnApiService = inject(WebauthnApiService);
    private webauthnService = inject(WebauthnService);
    private passkeySettingsApiService = inject(PasskeySettingsApiService);

    private dialogErrorSource = new Subject<string>();

    registeredPasskeys: PasskeyDto[] = [];

    dialogError$ = this.dialogErrorSource.asObservable();

    currentUser?: User;

    isEdit = false;

    private authStateSubscription: Subscription;

    ngOnInit() {
        // TODO use signals and fetch registered passkeys once user object is set
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser = user;
                    return this.currentUser;
                }),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        this.authStateSubscription.unsubscribe();
    }

    async updateRegisteredPasskeys() {
        if (this.currentUser?.id) {
            this.registeredPasskeys = await this.passkeySettingsApiService.getRegisteredPasskeys(this.currentUser?.id);
        }
    }

    async addNewPasskey() {
        await this.updateRegisteredPasskeys();
        // TODO add proper error handling
        const options = await this.webauthnApiService.getRegistrationOptions();
        const credentialOptions = this.createCredentialOptions(options);

        const credential = await navigator.credentials.create({
            publicKey: credentialOptions,
        });

        if (!credential) {
            alert('Credential is undefined');
            return;
        }

        const email = this.currentUser?.email;
        if (!email) {
            alert('Email is undefined');
            return;
        }

        await this.webauthnApiService.registerPasskey({
            publicKey: {
                credential: credential,
                label: email,
            },
        });

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

    private createCredentialOptions(options: PasskeyOptions): PublicKeyCredentialCreationOptions {
        const userId = this.currentUser?.id;

        if (!userId) {
            throw new Error('User ID is undefined');
        }
        const username = '' + userId; // TODO adjust properly

        // TODO verify values are set properly
        return {
            ...options,
            challenge: decodeBase64url(options.challenge),
            user: {
                id: new TextEncoder().encode(userId.toString()),
                name: username,
                displayName: username,
            },
            excludeCredentials: options.excludeCredentials.map((credential) => ({
                ...credential,
                id: decodeBase64url(credential.id),
            })),
            authenticatorSelection: {
                requireResidentKey: true,
                userVerification: 'discouraged',
            },
        };
    }

    deletePasskey() {
        // TODO
        this.alertService.addErrorAlert('Not implemented yet');
    }

    editPasskey() {
        // TODO
        this.alertService.addErrorAlert('Not implemented yet');
    }
}
