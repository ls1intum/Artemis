import { Component, Input, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AddUsersFormData } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/add-users-form.component';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ConversationDto, MAX_MEMBERS_IN_DIRECT_CONVERSATION } from 'app/entities/metis/conversation/conversation.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
})
export class ConversationAddUsersDialogComponent implements OnInit {
    readonly MAX_MEMBERS_IN_DIRECT_CONVERSATION = MAX_MEMBERS_IN_DIRECT_CONVERSATION;

    @Input()
    course: Course;

    @Input()
    activeConversation: ConversationDto;

    isInitialized = false;

    initialize() {
        if (!this.course || !this.activeConversation) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, public channelService: ChannelService, public conversationService: ConversationService) {}

    ngOnInit(): void {}

    onFormSubmitted($event: AddUsersFormData) {
        this.addUsers($event.selectedUsers ?? []);
    }

    clear() {
        this.activeModal.dismiss();
    }

    getAsChannel = getAsChannelDto;
    getAsGroupChat = getAsGroupChatDto;

    getConversationName = this.conversationService.getConversationName;

    private addUsers(usersToAdd: User[]) {
        const userLogins = usersToAdd.map((user) => user.login!);

        if (isChannelDto(this.activeConversation)) {
            this.channelService.registerUsersToChannel(this.course.id!, this.activeConversation.id!, userLogins).subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                    this.activeModal.close();
                },
            });
        }
    }
}
