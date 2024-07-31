import { Component, Signal, WritableSignal, computed, signal } from '@angular/core';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ButtonSize } from 'app/shared/components/button.component';
import { AdminUserService } from 'app/core/user/admin-user.service';

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

    constructor(private adminUserService: AdminUserService) {}

    loadUserList() {
        if (this.users()) {
            return;
        }

        // TODO server query to load user list
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
