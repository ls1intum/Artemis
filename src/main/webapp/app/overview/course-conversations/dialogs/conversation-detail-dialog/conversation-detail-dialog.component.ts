import { Component, Input, OnInit } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getConversationName } from 'app/shared/metis/conversations/conversation.util';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
})
export class ConversationDetailDialogComponent implements OnInit {
    @Input()
    public activeConversation: ConversationDto;
    @Input()
    course: Course;
    @Input()
    selectedTab: ConversationDetailTabs = ConversationDetailTabs.MEMBERS;

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
}
