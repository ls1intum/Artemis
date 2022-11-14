import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-one-to-one-chat-create-dialog',
    templateUrl: './one-to-one-chat-create-dialog.component.html',
    styleUrls: ['./one-to-one-chat-create-dialog.component.scss'],
})
export class OneToOneChatCreateDialogComponent {
    @Input()
    course: Course;

    isInitialized = false;
    selectedUsers: UserPublicInfoDTO[] = [];
    userToChatWith?: UserPublicInfoDTO;

    constructor(private activeModal: NgbActiveModal) {}

    initialize() {
        if (!this.course) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    clear() {
        this.activeModal.dismiss();
    }

    onUserSelected() {
        this.activeModal.close(this.userToChatWith);
    }

    onSelectedUsersChange(selectedUsers: UserPublicInfoDTO[]) {
        if (selectedUsers && selectedUsers.length > 0) {
            this.selectedUsers = selectedUsers;
            this.userToChatWith = this.selectedUsers[0];
            this.onUserSelected();
        }
    }
}
