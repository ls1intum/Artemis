import { Component, EventEmitter, Signal, WritableSignal, computed, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ButtonType } from 'app/shared/components/button.component';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Observable } from 'rxjs';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    standalone: true,
    selector: 'jhi-delete-users-button',
    templateUrl: './delete-users-button.component.html',
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
})
export class DeleteUsersButtonComponent {
    users: WritableSignal<string[] | undefined> = signal(undefined);
    usersString: Signal<string | undefined> = computed(() => this.users()?.join(', '));

    // TODO Handle like in user-management for the delete button?
    dialogError: Observable<string>;

    // Boilerplate code for use in the template
    faEraser = faEraser;

    constructor(
        private adminUserService: AdminUserService,
        private alertService: AlertService,
        private deleteDialogService: DeleteDialogService,
    ) {}

    loadUserList() {
        this.adminUserService.queryNotEnrolledUsers().subscribe({
            next: (res: HttpResponse<string[]>) => {
                if (res.body == null) {
                    throw new Error('Unexpected null response body for user list');
                } else {
                    this.users.set(res.body);
                    if (res.body.length == 0) {
                        this.alertService.info('artemisApp.userManagement.notEnrolled.delete.cancel');
                    } else {
                        this.openDeleteDialog();
                    }
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
        conformer.subscribe(this.onConfirm);
        const deleteDialogData: DeleteDialogData = {
            requireConfirmationOnlyForAdditionalChecks: false,
            entityTitle: (this.users() ?? []).length.toString(),
            deleteQuestion: 'artemisApp.userManagement.notEnrolled.delete.question',
            translateValues: { users: this.usersString() },
            deleteConfirmationText: 'artemisApp.userManagement.batch.delete.typeNumberToConfirm',
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: conformer,
            dialogError: this.dialogError,
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData, true);
    }

    onConfirm() {
        const logins = this.users();
        if (logins != undefined) {
            /*
             * Delete the list of confirmed users. Don't filter in the delete operation
             * to avoid that there are other users deleted than the confirmed ones.
             */
            this.adminUserService.deleteUsers(logins);
            // TODO show some feedback dialog (similar to delete selected users)
        } else {
            throw new Error('Unexpected undefined list of user logins');
        }
    }

    protected readonly ButtonType = ButtonType;
}
