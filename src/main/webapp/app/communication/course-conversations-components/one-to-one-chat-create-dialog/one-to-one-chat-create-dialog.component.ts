import { Component, Input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-one-to-one-chat-create-dialog',
    templateUrl: './one-to-one-chat-create-dialog.component.html',
    imports: [TranslateDirective, CourseUsersSelectorComponent, FormsModule, ArtemisTranslatePipe],
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
