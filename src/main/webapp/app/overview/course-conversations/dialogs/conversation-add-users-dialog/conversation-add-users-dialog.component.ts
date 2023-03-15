import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnDestroy } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject, takeUntil } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { getAsGroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { AddUsersFormData } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { MAX_GROUP_CHAT_PARTICIPANTS } from 'app/shared/metis/conversations/conversation-settings';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
})
export class ConversationAddUsersDialogComponent extends AbstractDialogComponent implements OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    @Input()
    course: Course;

    @Input()
    activeConversation: ConversationDto;

    isInitialized = false;

    maxSelectable: number | undefined;

    initialize() {
        super.initialize(['course', 'activeConversation']);
        if (this.isInitialized) {
            if (isGroupChatDto(this.activeConversation)) {
                this.maxSelectable = MAX_GROUP_CHAT_PARTICIPANTS - (this.activeConversation?.numberOfMembers ?? 0);
            }
        }
    }

    constructor(
        private alertService: AlertService,

        activeModal: NgbActiveModal,
        public channelService: ChannelService,
        public conversationService: ConversationService,
        public groupChatService: GroupChatService,
    ) {
        super(activeModal);
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onFormSubmitted({ selectedUsers, addAllStudents, addAllTutors, addAllInstructors }: AddUsersFormData) {
        this.addUsers(selectedUsers ?? [], addAllStudents, addAllTutors, addAllInstructors);
    }

    getAsChannel = getAsChannelDto;
    getAsGroupChat = getAsGroupChatDto;

    getConversationName = this.conversationService.getConversationName;

    private addUsers(usersToAdd: UserPublicInfoDTO[], addAllStudents: boolean, addAllTutors: boolean, addAllInstructors: boolean) {
        const userLogins = usersToAdd.map((user) => user.login!);

        if (isChannelDto(this.activeConversation)) {
            this.channelService
                .registerUsersToChannel(this.course.id!, this.activeConversation.id!, addAllStudents, addAllTutors, addAllInstructors, userLogins)
                .pipe(
                    finalize(() => this.close()),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe({
                    next: () => {},
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                    },
                });
        } else if (isGroupChatDto(this.activeConversation)) {
            this.groupChatService
                .addUsersToGroupChat(this.course.id!, this.activeConversation.id!, userLogins)
                .pipe(
                    finalize(() => this.close()),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe({
                    next: () => {},
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                    },
                });
        } else {
            throw new Error('Conversation type not supported');
        }
    }
}
