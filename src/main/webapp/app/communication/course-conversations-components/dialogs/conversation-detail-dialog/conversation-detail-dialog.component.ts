import { Component, inject, output, signal } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Course } from 'app/course/shared/entities/course.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { getAsOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { getAsGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { faPeopleGroup, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabValue, TumAetUiTabsComponent } from '@tumaet/ui-angular';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RouterLink } from '@angular/router';
import { ConversationMembersComponent } from './tabs/conversation-members/conversation-members.component';
import { ConversationInfoComponent } from './tabs/conversation-info/conversation-info.component';
import { ConversationSettingsComponent } from './tabs/conversation-settings/conversation-settings.component';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
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
        ArtemisTranslatePipe,
        RouterLink,
        ConversationMembersComponent,
        ConversationInfoComponent,
        ConversationSettingsComponent,
        ProfilePictureComponent,
        TumAetUiButtonDirective,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
    ],
})
export class ConversationDetailDialogComponent extends AbstractDialogComponent {
    conversationService = inject(ConversationService);

    activeConversation = signal<ConversationDTO | undefined>(undefined);
    course = signal<Course | undefined>(undefined);
    readonly selectedTab = signal<ConversationDetailTabs>(ConversationDetailTabs.MEMBERS);

    override isInitialized = false;
    readonly isOneToOneChat = signal(false);
    readonly otherUser = signal<ConversationUserDTO | undefined>(undefined);
    readonly faPeopleGroup = faPeopleGroup;
    protected readonly faXmark = faXmark;
    readonly userNameClicked = output<number>();

    override initialize() {
        super.initialize(['course', 'activeConversation', 'selectedTab']);
        const activeConversation = this.activeConversation();
        if (activeConversation) {
            const conversation = getAsOneToOneChatDTO(activeConversation);
            if (conversation) {
                this.isOneToOneChat.set(true);
                this.otherUser.set(conversation.members?.find((user) => !user.isRequestingUser));
            }
        }
    }

    getAsChannel = getAsChannelDTO;
    getAsGroupChat = getAsGroupChatDTO;

    readonly changesWerePerformed = signal(false);

    Tabs = ConversationDetailTabs;

    /** The tab list reports the selected tab as a plain value, so only a known tab is taken over. */
    onTabChange(value: TumAetUiTabValue) {
        const tab = Object.values(ConversationDetailTabs).find((candidate) => candidate === value);
        if (tab) {
            this.selectedTab.set(tab);
        }
    }

    clear() {
        if (this.changesWerePerformed()) {
            this.close(true);
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
        this.changesWerePerformed.set(true);
        this.clear();
    }

    /**
     * Emits the user ID when a username is clicked, allowing the parent to handle user-specific actions.
     */
    onUserNameClicked(userId: number) {
        this.userNameClicked.emit(userId);
        // Also invoke callback passed via dialog data (for callers opening the dialog through the dialog service)
        const callback = this.dialogConfig?.data?.onUserNameClicked;
        if (typeof callback === 'function') {
            callback(userId);
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
