import { EventEmitter, Injectable, inject } from '@angular/core';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { CredentialRevocationChoice } from 'app/account/password/password.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

/**
 * Asks the user to confirm before credentials are deleted, naming exactly which ones.
 * <p>
 * Revoking passkeys, SSH keys or access tokens cannot be undone, and it is reachable from four places: a password
 * change, a completed password reset, an administrator editing a user, and the revocation settings page. Deleting a
 * single SSH key has always asked for confirmation, so wiping all three categories in one submit must too - and the
 * reset flow arrives with every option preselected, which makes it the easiest one to trigger by accident.
 * <p>
 * One service rather than a dialog per form so all four sites ask the same question and cannot drift apart, and so the
 * question can name the selected categories instead of saying "the selected credentials" - on the reset path the
 * default is all of them, which the user should see spelled out before they agree to it.
 */
@Injectable({ providedIn: 'root' })
export class CredentialRevocationConfirmationService {
    private readonly deleteDialogService = inject(DeleteDialogService);
    private readonly translateService = inject(TranslateService);

    /**
     * Asks the user to confirm deleting the selected credentials.
     * <p>
     * Resolves immediately with {@code true} when the choice revokes nothing: a routine password change must not grow a
     * dialog, both because there is nothing to warn about and because a confirmation shown every time is one users learn
     * to click through - which would weaken it exactly where it matters.
     *
     * @param choice which credential types are about to be revoked
     * @returns {@code true} if the action may proceed, {@code false} if the user dismissed the dialog
     */
    confirm(choice: CredentialRevocationChoice | undefined): Promise<boolean> {
        if (!choice || !(choice.passkeys || choice.sshKeys || choice.vcsAccessTokens)) {
            return Promise.resolve(true);
        }

        return new Promise<boolean>((resolve) => {
            let confirmed = false;
            const dialogErrorSource = new Subject<string>();
            const deleteEmitter = new EventEmitter<{ [key: string]: boolean }>();

            deleteEmitter.subscribe(() => {
                confirmed = true;
                // An empty error closes the dialog; the caller reports its own failure afterwards.
                dialogErrorSource.next('');
                resolve(true);
            });

            this.deleteDialogService.openDeleteDialog({
                entityTitle: '',
                deleteQuestion: 'artemisApp.credentialRevocation.confirmQuestion',
                translateValues: { credentials: this.describe(choice) },
                actionType: ActionType.Remove,
                buttonType: ButtonType.ERROR,
                delete: deleteEmitter,
                dialogError: dialogErrorSource.asObservable(),
                // No additional checks to tick, so the confirm button is enabled from the start.
                requireConfirmationOnlyForAdditionalChecks: false,
            });

            this.deleteDialogService.dialogRef()?.onClose.subscribe(() => {
                // Dismissing the dialog must leave the credentials alone rather than fall through to the action.
                if (!confirmed) {
                    resolve(false);
                }
            });
        });
    }

    /**
     * Lists the selected categories in the user's language, so the dialog says what will be deleted rather than
     * referring vaguely to a selection the user has to scroll back to check.
     */
    private describe(choice: CredentialRevocationChoice): string {
        const names: string[] = [];
        if (choice.passkeys) {
            names.push(this.translateService.instant('artemisApp.credentialRevocation.types.passkeys'));
        }
        if (choice.sshKeys) {
            names.push(this.translateService.instant('artemisApp.credentialRevocation.types.sshKeys'));
        }
        if (choice.vcsAccessTokens) {
            names.push(this.translateService.instant('artemisApp.credentialRevocation.types.vcsAccessTokens'));
        }
        return names.join(', ');
    }
}
