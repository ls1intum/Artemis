import { Component, Input, OnDestroy } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AddUsersFormData } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
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
import { Subject, takeUntil } from 'rxjs';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
})
export class ConversationAddUsersDialogComponent extends AbstractDialogComponent implements OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    @Input() course: Course;
    @Input() activeConversation: ConversationDto;

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
