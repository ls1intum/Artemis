import { Component, Signal, WritableSignal, computed, signal } from '@angular/core';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ButtonSize } from 'app/shared/components/button.component';

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

    loadUserList() {
        if (this.users()) {
            return;
        }

        // TODO server query to load user list
    }

    onConfirm() {
        // TODO call to server to delete users
    }
}
