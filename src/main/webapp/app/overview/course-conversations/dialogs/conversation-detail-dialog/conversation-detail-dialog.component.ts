import { Component, Input, OnInit } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
    INFO = 'info',
    SETTINGS = 'settings',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
    styleUrls: ['./conversation-detail-dialog.component.scss'],
})
export class ConversationDetailDialogComponent implements OnInit {
    @Input()
    public activeConversation: ConversationDto;
    @Input()
    course: Course;
    @Input()
    selectedTab: ConversationDetailTabs = ConversationDetailTabs.MEMBERS;

    isInitialized = false;

    initialize() {
        if (!this.course || !this.activeConversation || !this.selectedTab) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    getAsChannel = getAsChannelDto;
    getConversationName = this.conversationService.getConversationName;

    isOneToOneChat = isOneToOneChatDto;

    changesWerePerformed = false;

    Tabs = ConversationDetailTabs;
    constructor(private activeModal: NgbActiveModal, private conversationService: ConversationService) {}

    ngOnInit(): void {}
    clear() {
        if (this.changesWerePerformed) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss();
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
