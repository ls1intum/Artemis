import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

@Component({
    selector: 'jhi-one-to-one-chat-create-dialog',
    templateUrl: './one-to-one-chat-create-dialog.component.html',
})
export class OneToOneChatCreateDialogComponent extends AbstractDialogComponent {
    @Input() course: Course;

    isInitialized = false;
    selectedUsers: UserPublicInfoDTO[] = [];
    userToChatWith?: UserPublicInfoDTO;

    initialize() {
        super.initialize(['course']);
    }

    clear() {
        this.dismiss();
    }

    onUserSelected() {
        this.close(this.userToChatWith);
    }

    onSelectedUsersChange(selectedUsers: UserPublicInfoDTO[]) {
        if (selectedUsers && selectedUsers.length > 0) {
            this.selectedUsers = selectedUsers;
            this.userToChatWith = this.selectedUsers[0];
            this.onUserSelected();
        }
    }
}
