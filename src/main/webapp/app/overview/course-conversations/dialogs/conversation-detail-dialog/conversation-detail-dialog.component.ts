import { Component, Input, OnInit } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getConversationName } from 'app/shared/metis/conversations/conversation.util';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
    INFO = 'info',
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
    getConversationName = getConversationName;

    changesWerePerformed = false;

    Tabs = ConversationDetailTabs;
    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit(): void {}
    clear() {
        if (this.changesWerePerformed) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss();
        }
    }

    onChannelLeave() {
        this.changesWerePerformed = true;
        this.clear();
    }
}
