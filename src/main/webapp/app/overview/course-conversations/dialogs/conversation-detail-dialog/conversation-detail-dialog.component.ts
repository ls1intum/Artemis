import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

import { Course } from 'app/entities/course.model';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
    INFO = 'info',
    SETTINGS = 'settings',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
})
export class ConversationDetailDialogComponent extends AbstractDialogComponent {
    @Input()
    public activeConversation: ConversationDto;
    @Input()
    course: Course;
    @Input()
    selectedTab: ConversationDetailTabs = ConversationDetailTabs.MEMBERS;

    isInitialized = false;

    initialize() {
        super.initialize(['course', 'activeConversation', 'selectedTab']);
    }

    isOneToOneChat = isOneToOneChatDto;
    getAsChannel = getAsChannelDto;
    getAsGroupChat = getAsGroupChatDto;
    getConversationName = this.conversationService.getConversationName;

    changesWerePerformed = false;

    Tabs = ConversationDetailTabs;
    constructor(activeModal: NgbActiveModal, private conversationService: ConversationService) {
        super(activeModal);
    }

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
