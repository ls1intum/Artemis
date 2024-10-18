import { Component, EventEmitter, Output, WritableSignal, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ButtonType } from 'app/shared/components/button.component';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Subject } from 'rxjs';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

/**
 * Button to delete not enrolled users with a confirmation dialog
 * that shows a list of the logins of the users which will be deleted.
 */
@Component({
    standalone: true,
    selector: 'jhi-delete-users-button',
    templateUrl: './delete-users-button.component.html',
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
})
export class DeleteUsersButtonComponent {
    private adminUserService = inject(AdminUserService);
    private alertService = inject(AlertService);
    private deleteDialogService = inject(DeleteDialogService);

    @Output() deletionCompleted = new EventEmitter<{ [key: string]: boolean }>();

    users: WritableSignal<string[] | undefined> = signal(undefined);

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    // Boilerplate code for use in the template
    faEraser = faEraser;
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
