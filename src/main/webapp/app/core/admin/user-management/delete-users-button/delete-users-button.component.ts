import { ChangeDetectionStrategy, Component, EventEmitter, inject, output, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faEraser } from '@fortawesome/free-solid-svg-icons';

import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { Subject } from 'rxjs';

/**
 * Button to delete not enrolled users with a confirmation dialog
 * that shows a list of the logins of the users which will be deleted.
 */
@Component({
    selector: 'jhi-delete-users-button',
    templateUrl: './delete-users-button.component.html',
    imports: [ButtonComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeleteUsersButtonComponent {
    private readonly adminUserService = inject(AdminUserService);
    private readonly alertService = inject(AlertService);
    private readonly deleteDialogService = inject(DeleteDialogService);

    /** Emitted when deletion is completed */
    readonly deletionCompleted = output<void>();

    /** List of users to be deleted */
    readonly users = signal<string[] | undefined>(undefined);

    /** Subject for dialog error messages */
    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError = this.dialogErrorSource.asObservable();

    /** Icons */
    protected readonly faEraser = faEraser;
    protected readonly ButtonType = ButtonType;

    /**
     * Load the list of users to user confirmation and delete.
     */
    loadUserList() {
        this.adminUserService.queryNotEnrolledUsers().subscribe({
            next: (res: HttpResponse<string[]>) => {
                const users = res.body!;
                this.users.set(users);
                if (users.length === 0) {
                    this.alertService.info('artemisApp.userManagement.notEnrolled.delete.cancel');
                } else {
                    this.openDeleteDialog();
                }
            },
            error: (res: HttpErrorResponse) => {
                onError(this.alertService, res);
            },
        });
    }

    /**
     * Opens delete dialog
     */
    openDeleteDialog() {
        const conformer = new EventEmitter<any>();
        conformer.subscribe(() => this.onConfirm());
        const deleteDialogData: DeleteDialogData = {
            requireConfirmationOnlyForAdditionalChecks: false,
            entityTitle: (this.users() ?? []).length.toString(),
            deleteQuestion: 'artemisApp.userManagement.notEnrolled.delete.question',
            translateValues: { users: this.users()?.join(', ') },
            deleteConfirmationText: 'artemisApp.userManagement.batch.delete.typeNumberToConfirm',
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: conformer,
            dialogError: this.dialogError,
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData, true);
    }

    /**
     * Method for the actions after the user confirmed the deletion.
     */
    onConfirm() {
        const logins = this.users();
        if (!logins) {
            return;
        }

        /*
         * Delete the list of confirmed users. Don't filter in the delete operation
         * to avoid that there are other users deleted than the confirmed ones.
         */
        this.adminUserService.deleteUsers(logins).subscribe({
            next: () => {
                this.deletionCompleted.emit();
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
