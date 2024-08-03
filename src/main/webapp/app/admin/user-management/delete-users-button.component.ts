import { Component, Signal, WritableSignal, computed, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ButtonSize } from 'app/shared/components/button.component';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    standalone: true,
    selector: 'jhi-delete-users-button',
    templateUrl: './delete-users-button.component.html',
    imports: [ArtemisSharedModule],
})
export class DeleteUsersButtonComponent {
    users: WritableSignal<string[] | undefined> = signal(undefined);
    usersString: Signal<string | undefined> = computed(() => this.users()?.join(', '));

    // Boilerplate code for use in the template
    faEraser = faEraser;
    readonly medium = ButtonSize.MEDIUM;

    constructor(
        private adminUserService: AdminUserService,
        private alertService: AlertService,
    ) {}

    loadUserList() {
        this.adminUserService.queryNotEnrolledUsers().subscribe({
            next: (res: HttpResponse<string[]>) => {
                if (res.body == null) {
                    throw new Error('Unexpected null response body for user list');
                } else {
                    this.users.set(res.body);
                    // TODO lists in the alerts contain items, but does not get to the dialog ...
                    this.alertService.info('TEST: ' + (this.usersString() ?? 'empty ...'));
                    if (res.body.length == 0) {
                        this.alertService.info('artemisApp.userManagement.notEnrolled.delete.cancel');
                        // TODO Cancel the delete conformation dialog
                    }
                }
            },
            error: (res: HttpErrorResponse) => {
                onError(this.alertService, res);
            },
        });
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
}
