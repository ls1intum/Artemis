import { Component, OnDestroy, inject, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { getAsGroupChatDTO, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { Subject, takeUntil } from 'rxjs';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { finalize } from 'rxjs/operators';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AddUsersFormData, ConversationAddUsersFormComponent } from './add-users-form/conversation-add-users-form.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { MAX_GROUP_CHAT_PARTICIPANTS } from 'app/communication/conversations/conversation-settings';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
    imports: [ChannelIconComponent, TranslateDirective, ConversationAddUsersFormComponent, ArtemisTranslatePipe],
})
export class ConversationAddUsersDialogComponent extends AbstractDialogComponent implements OnDestroy {
    private alertService = inject(AlertService);
    channelService = inject(ChannelService);
    conversationService = inject(ConversationService);
    groupChatService = inject(GroupChatService);

    private ngUnsubscribe = new Subject<void>();

    course = signal<Course | undefined>(undefined);
    activeConversation = signal<ConversationDTO | undefined>(undefined);

    isInitialized = false;
    maxSelectable: number | undefined;
    protected isLoading = false;

    initialize() {
        super.initialize(['course', 'activeConversation']);
        if (this.isInitialized) {
            const activeConversation = this.activeConversation()!;
            if (isGroupChatDTO(activeConversation)) {
                this.maxSelectable = MAX_GROUP_CHAT_PARTICIPANTS - (activeConversation?.numberOfMembers ?? 0);
            }
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onFormSubmitted({ selectedUsers, addAllStudents, addAllTutors, addAllInstructors }: AddUsersFormData) {
        this.addUsers(selectedUsers ?? [], addAllStudents, addAllTutors, addAllInstructors);
    }

    getAsChannel = getAsChannelDTO;
    getAsGroupChat = getAsGroupChatDTO;

    private addUsers(usersToAdd: UserPublicInfoDTO[], addAllStudents: boolean, addAllTutors: boolean, addAllInstructors: boolean) {
        const userLogins = usersToAdd.map((user) => user.login!);

        this.isLoading = true;

        const activeConversation = this.activeConversation()!;
        if (isChannelDTO(activeConversation)) {
            this.channelService
                .registerUsersToChannel(this.course()!.id!, activeConversation.id!, addAllStudents, addAllTutors, addAllInstructors, userLogins)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe({
                    next: () => {
                        this.close(true);
                    },
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                    },
                });
        } else if (isGroupChatDTO(activeConversation)) {
            this.groupChatService
                .addUsersToGroupChat(this.course()!.id!, activeConversation.id!, userLogins)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe({
                    next: () => {
                        this.close(true);
                    },
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                    },
                });
        } else {
            throw new Error('Conversation type not supported');
        }
    }
}
