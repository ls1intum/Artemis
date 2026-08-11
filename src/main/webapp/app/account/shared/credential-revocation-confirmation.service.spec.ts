import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType, DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';

/**
 * The four revocation sites stub this service, so nothing else exercises it. Its own behaviour is what decides whether a
 * user is asked before their credentials are deleted, and whether dismissing the dialog actually stops the action.
 */
describe('CredentialRevocationConfirmationService', () => {
    let service: CredentialRevocationConfirmationService;
    let openDeleteDialog: ReturnType<typeof vi.fn>;
    let dialogRef: ReturnType<typeof signal<{ onClose: Subject<unknown> } | undefined>>;
    let onClose: Subject<unknown>;

    beforeEach(() => {
        onClose = new Subject<unknown>();
        dialogRef = signal<{ onClose: Subject<unknown> } | undefined>(undefined);
        // Mirrors the real service: opening the dialog is what makes dialogRef() readable.
        openDeleteDialog = vi.fn(() => dialogRef.set({ onClose }));

        TestBed.configureTestingModule({
            providers: [
                CredentialRevocationConfirmationService,
                { provide: DeleteDialogService, useValue: { openDeleteDialog, dialogRef } },
                { provide: TranslateService, useValue: { instant: (key: string) => key } },
            ],
        });
        service = TestBed.inject(CredentialRevocationConfirmationService);
    });

    /** The data the service handed to the dialog, so the question and its values can be asserted. */
    function openedWith(): DeleteDialogData {
        return openDeleteDialog.mock.calls[0][0] as DeleteDialogData;
    }

    it.each([
        { choice: undefined, description: 'no choice at all' },
        { choice: { passkeys: false, sshKeys: false, vcsAccessTokens: false }, description: 'a choice that revokes nothing' },
    ])('should proceed without asking for $description', async ({ choice }) => {
        // A routine password change must not grow a dialog: there is nothing to warn about, and a confirmation shown every
        // time is one users learn to click through.
        await expect(service.confirm(choice)).resolves.toBe(true);
        expect(openDeleteDialog).not.toHaveBeenCalled();
    });

    it('should ask before anything is deleted and proceed once confirmed', async () => {
        const confirmation = service.confirm({ passkeys: true, sshKeys: false, vcsAccessTokens: false });

        expect(openDeleteDialog).toHaveBeenCalledOnce();
        expect(openedWith().actionType).toBe(ActionType.Remove);
        expect(openedWith().deleteQuestion).toBe('artemisApp.credentialRevocation.confirmQuestion');
        openedWith().delete.emit({});

        await expect(confirmation).resolves.toBe(true);
    });

    it('should not proceed when the dialog is dismissed', async () => {
        // The whole point: a dismissal has to stop the action rather than fall through to it.
        const confirmation = service.confirm({ passkeys: true, sshKeys: true, vcsAccessTokens: true });
        onClose.next(undefined);

        await expect(confirmation).resolves.toBe(false);
    });

    it('should close the dialog on confirmation', async () => {
        const confirmation = service.confirm({ passkeys: false, sshKeys: true, vcsAccessTokens: false });
        const dialogErrors: string[] = [];
        openedWith().dialogError!.subscribe((value) => dialogErrors.push(value));

        openedWith().delete.emit({});
        await confirmation;

        // An empty error closes the dialog; the caller reports its own failure afterwards.
        expect(dialogErrors).toEqual(['']);
    });

    it('should resolve only once when a confirmed dialog also emits onClose', async () => {
        // The dialog closes after being confirmed, so both paths fire. The confirmation must stay `true`.
        const confirmation = service.confirm({ passkeys: true, sshKeys: false, vcsAccessTokens: false });
        openedWith().delete.emit({});
        onClose.next(undefined);

        await expect(confirmation).resolves.toBe(true);
    });

    it.each([
        { choice: { passkeys: true, sshKeys: true, vcsAccessTokens: true }, expected: 'passkeys, sshKeys, vcsAccessTokens' },
        { choice: { passkeys: false, sshKeys: true, vcsAccessTokens: false }, expected: 'sshKeys' },
        { choice: { passkeys: true, sshKeys: false, vcsAccessTokens: true }, expected: 'passkeys, vcsAccessTokens' },
    ])('should name only the selected categories in the question', async ({ choice, expected }) => {
        // The question names what will be deleted rather than referring to a selection the user has to scroll back to
        // check - on the reset path the default is all three, which they should see spelled out.
        const confirmation = service.confirm(choice);
        const credentials = openedWith().translateValues!.credentials as string;

        expect(credentials).toBe(expected.replace(/(\w+)/g, 'artemisApp.credentialRevocation.types.$1'));

        openedWith().delete.emit({});
        await confirmation;
    });
});
