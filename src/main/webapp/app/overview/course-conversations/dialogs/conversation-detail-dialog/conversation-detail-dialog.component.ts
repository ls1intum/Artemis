import { Component, Input, inject } from '@angular/core';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { getAsGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { ChannelIconComponent } from '../../other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ConversationMembersComponent } from './tabs/conversation-members/conversation-members.component';
import { ConversationInfoComponent } from './tabs/conversation-info/conversation-info.component';
import { ConversationSettingsComponent } from './tabs/conversation-settings/conversation-settings.component';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
    INFO = 'info',
    SETTINGS = 'settings',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
    imports: [ChannelIconComponent, FaIconComponent, TranslateDirective, RouterLink, ConversationMembersComponent, ConversationInfoComponent, ConversationSettingsComponent],
})
export class ConversationDetailDialogComponent extends AbstractDialogComponent {
    conversationService = inject(ConversationService);

    @Input() public activeConversation: ConversationDTO;
    @Input() course: Course;
    @Input() selectedTab: ConversationDetailTabs = ConversationDetailTabs.MEMBERS;

    isInitialized = false;
    readonly faPeopleGroup = faPeopleGroup;

    initialize() {
        super.initialize(['course', 'activeConversation', 'selectedTab']);
    }

    isOneToOneChat = isOneToOneChatDTO;
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

    onChannelDeleted() {
        this.markAsChangedAndClose();
    }

    private markAsChangedAndClose() {
        this.changesWerePerformed = true;
        this.clear();
    }
}
