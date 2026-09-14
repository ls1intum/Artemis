import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumUiButtonDirective, TumUiCheckboxComponent } from '@tumaet/ui-angular';
import { CredentialRevocationChoice } from 'app/account/password/password.service';
import { CredentialRevocationService } from 'app/account/user/settings/credential-revocation-settings/credential-revocation.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { Subject } from 'rxjs';
import { onError } from 'app/foundation/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';

/**
 * Lets a user revoke their passkeys, SSH keys and repository access tokens without changing their password.
 * <p>
 * Exists because the revocation offered alongside a password change is unreachable for external users: their password
 * lives in their identity provider, so Artemis refuses the change outright and the whole page stays empty for them.
 * Without this they would have to delete every passkey, key and token individually across three settings pages, which is
 * exactly the situation someone acting on a suspected compromise should not be in.
 * <p>
 * Nothing is preselected. Each option costs the user something real -- re-registering devices, re-adding keys -- so the
 * page asks them to say what they want gone rather than defaulting to all of it, and the confirmation dialog spells out
 * the consequence before anything is deleted.
 */
@Component({
    selector: 'jhi-credential-revocation-settings',
    templateUrl: './credential-revocation-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [TranslateDirective, FormsModule, TumUiCheckboxComponent, DeleteButtonDirective, TumUiButtonDirective],
})
export class CredentialRevocationSettingsComponent implements OnDestroy {
    private readonly credentialRevocationService = inject(CredentialRevocationService);
    private readonly alertService = inject(AlertService);

    protected readonly ActionType = ActionType;

    readonly revokePasskeys = signal(false);
    readonly revokeSshKeys = signal(false);
    readonly revokeVcsAccessTokens = signal(false);
    readonly isRevoking = signal(false);

    /** The server rejects a request that selects nothing, so the action stays disabled until something is selected. */
    readonly hasSelection = computed(() => this.revokePasskeys() || this.revokeSshKeys() || this.revokeVcsAccessTokens());

    private readonly dialogErrorSource = new Subject<string>();

    readonly dialogError$ = this.dialogErrorSource.asObservable();

    ngOnDestroy(): void {
        this.dialogErrorSource.complete();
    }

    /**
     * Revokes the selected credential types and clears the selection, so the page cannot be used to fire the same
     * destructive request twice by accident.
     */
    revokeSelectedCredentials(): void {
        const choice: CredentialRevocationChoice = {
            passkeys: this.revokePasskeys(),
            sshKeys: this.revokeSshKeys(),
            vcsAccessTokens: this.revokeVcsAccessTokens(),
        };
        this.isRevoking.set(true);
        this.credentialRevocationService.revokeCredentials(choice).subscribe({
            next: () => {
                this.isRevoking.set(false);
                // Closes the confirmation dialog. DeleteDialogService raises an alert for any non-empty value it sees
                // here, so this must stay empty on both paths and the reporting is left to onError below.
                this.dialogErrorSource.next('');
                this.revokePasskeys.set(false);
                this.revokeSshKeys.set(false);
                this.revokeVcsAccessTokens.set(false);
                this.alertService.success('artemisApp.userSettings.credentialRevocation.success');
            },
            error: (error: HttpErrorResponse) => {
                this.isRevoking.set(false);
                // Empty, not the message: DeleteDialogService would otherwise raise its own alert with the raw
                // HttpErrorResponse text on top of the translated one onError produces, showing the user two alerts for
                // one failure. The dialog still needs a value to close.
                this.dialogErrorSource.next('');
                onError(this.alertService, error);
            },
        });
    }
}
