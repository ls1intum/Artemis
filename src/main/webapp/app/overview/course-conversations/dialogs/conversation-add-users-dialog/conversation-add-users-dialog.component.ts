import { Component, Input, OnDestroy, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { AddUsersFormData } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { getAsChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { getAsGroupChatDTO, isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { MAX_GROUP_CHAT_PARTICIPANTS } from 'app/shared/metis/conversations/conversation-settings';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { Subject, takeUntil } from 'rxjs';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { finalize } from 'rxjs/operators';
import { ChannelIconComponent } from '../../other/channel-icon/channel-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConversationAddUsersFormComponent } from './add-users-form/conversation-add-users-form.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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

    @Input() course: Course;
    @Input() activeConversation: ConversationDTO;

    isInitialized = false;
    maxSelectable: number | undefined;
    protected isLoading = false;

    initialize() {
        super.initialize(['course', 'activeConversation']);
        if (this.isInitialized) {
            if (isGroupChatDTO(this.activeConversation)) {
                this.maxSelectable = MAX_GROUP_CHAT_PARTICIPANTS - (this.activeConversation?.numberOfMembers ?? 0);
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

        if (isChannelDTO(this.activeConversation)) {
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
                    complete: () => {
                        this.isLoading = false;
                    },
                });
        } else if (isGroupChatDTO(this.activeConversation)) {
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
                    complete: () => {
                        this.isLoading = false;
                    },
                });
        } else {
            throw new Error('Conversation type not supported');
        }
    }
}
