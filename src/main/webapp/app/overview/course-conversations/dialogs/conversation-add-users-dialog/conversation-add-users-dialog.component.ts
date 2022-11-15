import { Component, Input, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AddUsersFormData } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/add-users-form.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getAsGroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { MAX_GROUP_CHAT_PARTICIPANTS } from 'app/shared/metis/conversations/conversation-settings';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
})
export class ConversationAddUsersDialogComponent implements OnInit {
    @Input()
    course: Course;

    @Input()
    activeConversation: ConversationDto;

    isInitialized = false;

    maxSelectable: number | undefined;

    initialize() {
        if (!this.course || !this.activeConversation) {
            console.error('Error: Dialog not fully configured');
        } else {
            if (isGroupChatDto(this.activeConversation)) {
                this.maxSelectable = MAX_GROUP_CHAT_PARTICIPANTS - (this.activeConversation?.numberOfMembers ?? 0);
            }

            this.isInitialized = true;
        }
    }

    constructor(
        private alertService: AlertService,
        private activeModal: NgbActiveModal,
        public channelService: ChannelService,
        public conversationService: ConversationService,
        public groupChatService: GroupChatService,
    ) {}

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

    private addUsers(usersToAdd: UserPublicInfoDTO[]) {
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
        } else if (isGroupChatDto(this.activeConversation)) {
            this.groupChatService.addUsersToGroupChat(this.course.id!, this.activeConversation.id!, userLogins).subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                    this.activeModal.close();
                },
            });
        } else {
            throw new Error('Conversation type not supported');
        }
    }
}
