import { Component, Input, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AddUsersFormData } from 'app/overview/course-messages/conversation-add-users-dialog/add-users-form/add-users-form.component';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { Conversation, MAX_MEMBERS_IN_DIRECT_CONVERSATION } from 'app/entities/metis/conversation/conversation.model';
import { ConversationType } from 'app/shared/metis/metis.util';
import { ConversationService } from 'app/shared/metis/conversation.service';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
    styleUrls: ['./conversation-add-users-dialog.component.scss'],
})
export class ConversationAddUsersDialogComponent implements OnInit {
    readonly CHANNEL = ConversationType.CHANNEL;
    readonly DIRECT = ConversationType.DIRECT;
    readonly MAX_MEMBERS_IN_DIRECT_CONVERSATION = MAX_MEMBERS_IN_DIRECT_CONVERSATION;

    @Input()
    course: Course;

    @Input()
    conversation: Conversation;

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, public conversationService: ConversationService) {}

    ngOnInit(): void {}

    onFormSubmitted($event: AddUsersFormData) {
        this.addUsers($event.selectedUsers ?? []);
    }

    clear() {
        this.activeModal.dismiss();
    }

    private addUsers(usersToAdd: User[]) {
        const userLogins = usersToAdd.map((user) => user.login!);
        this.conversationService.registerUsers(this.course.id!, this.conversation.id!, userLogins).subscribe();
    }
}
