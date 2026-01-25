import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faKey, faPencil, faPlus, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { Observable, Subject, Subscription, of, tap } from 'rxjs';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';
import { PasskeySettingsApiService } from 'app/core/user/settings/passkey-settings/passkey-settings-api.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ActionType, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CustomMaxLengthDirective } from 'app/shared/validators/custom-max-length-validator/custom-max-length-validator.directive';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { BadgeModule } from 'primeng/badge';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Authority, IS_AT_LEAST_ADMIN } from 'app/shared/constants/authority.constants';

export interface DisplayedPasskey extends PasskeyDTO {
    isEditingLabel?: boolean;
    labelBeforeEdit?: string;
}

@Component({
    selector: 'jhi-passkey-settings',
    imports: [
        TranslateDirective,
        FaIconComponent,
        DeleteButtonDirective,
        ArtemisDatePipe,
        ButtonComponent,
        CommonModule,
        FormsModule,
        CustomMaxLengthDirective,
        BadgeModule,
        ArtemisTranslatePipe,
    ],
    templateUrl: './passkey-settings.component.html',
    styleUrls: ['../user-settings.scss'],
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

    protected readonly alertService = inject(AlertService);
    protected readonly webauthnService = inject(WebauthnService);
    private readonly accountService = inject(AccountService);
    private readonly passkeySettingsApiService = inject(PasskeySettingsApiService);

    private dialogErrorSource = new Subject<string>();

    registeredPasskeys = signal<DisplayedPasskey[]>([]);

    dialogError$ = this.dialogErrorSource.asObservable();

    currentUser = signal<User | undefined>(undefined);

    isAdmin = computed(() => {
        const authorities = this.currentUser()?.authorities ?? [];
        return authorities.some((authority) => IS_AT_LEAST_ADMIN.includes(authority as Authority));
    });

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

    async addPasskey() {
        await this.webauthnService.addNewPasskey(this.currentUser());
        await this.updateRegisteredPasskeys();
    }

    async updateRegisteredPasskeys(): Promise<void> {
        this.registeredPasskeys.set(await this.passkeySettingsApiService.getRegisteredPasskeys());

        if (this.registeredPasskeys().length === 0) {
            this.accountService.userIdentity.set({
                ...this.accountService.userIdentity(),
                askToSetupPasskey: true,
                internal: this.accountService.userIdentity()?.internal ?? false,
            });
        }
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
