import { Component, Input, OnInit } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { getAsChannel } from 'app/entities/metis/conversation/channel.model';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
    styleUrls: ['./conversation-detail-dialog.component.scss'],
})
export class ConversationDetailDialogComponent implements OnInit {
    @Input()
    conversation: Conversation;
    @Input()
    course: Course;
    @Input()
    selectedTab: ConversationDetailTabs = ConversationDetailTabs.MEMBERS;

    getAsChannel = getAsChannel;

    changesWerePerformed = false;

    Tabs = ConversationDetailTabs;
    constructor(public conversationService: ConversationService, private activeModal: NgbActiveModal) {}

    ngOnInit(): void {}
    clear() {
        if (this.changesWerePerformed) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss();
        }
    }
}
