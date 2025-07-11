import { Component, Input, inject, output } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { getAsOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { getAsGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ConversationMembersComponent } from './tabs/conversation-members/conversation-members.component';
import { ConversationInfoComponent } from './tabs/conversation-info/conversation-info.component';
import { ConversationSettingsComponent } from './tabs/conversation-settings/conversation-settings.component';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
    INFO = 'info',
    SETTINGS = 'settings',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
    imports: [
        ChannelIconComponent,
        FaIconComponent,
        TranslateDirective,
        RouterLink,
        ConversationMembersComponent,
        ConversationInfoComponent,
        ConversationSettingsComponent,
        ProfilePictureComponent,
    ],
})
export class ConversationDetailDialogComponent extends AbstractDialogComponent {
    conversationService = inject(ConversationService);

    @Input() public activeConversation: ConversationDTO;
    @Input() course: Course;
    @Input() selectedTab: ConversationDetailTabs = ConversationDetailTabs.MEMBERS;

    isInitialized = false;
    isOneToOneChat = false;
    otherUser?: ConversationUserDTO;
    readonly faPeopleGroup = faPeopleGroup;
    readonly userNameClicked = output<number>();

    initialize() {
        super.initialize(['course', 'activeConversation', 'selectedTab']);
        if (this.activeConversation) {
            const conversation = getAsOneToOneChatDTO(this.activeConversation);
            if (conversation) {
                this.isOneToOneChat = true;
                this.otherUser = conversation.members?.find((user) => !user.isRequestingUser);
            }
        }
    }

    getAsChannel = getAsChannelDTO;
    getAsGroupChat = getAsGroupChatDTO;

    changesWerePerformed = false;

    Tabs = ConversationDetailTabs;

    clear() {
        if (this.changesWerePerformed) {
            this.close();
        } else {
            this.dismiss();
        }
    }

    onConversationLeave() {
        this.markAsChangedAndClose();
    }

    onArchivalChange() {
        this.markAsChangedAndClose();
    }

    /**
     * Callback for when the privacy setting is changed.
     */
    onPrivacyChange() {
        this.markAsChangedAndClose();
    }

    onChannelDeleted() {
        this.markAsChangedAndClose();
    }

    private markAsChangedAndClose() {
        this.changesWerePerformed = true;
        this.clear();
    }

    /**
     * Emits the user ID when a username is clicked, allowing the parent to handle user-specific actions.
     */
    onUserNameClicked(userId: number) {
        this.userNameClicked.emit(userId);
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
