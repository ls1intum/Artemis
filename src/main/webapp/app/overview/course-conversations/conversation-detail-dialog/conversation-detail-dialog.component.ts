import { Component, Input, OnInit } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getConversationName } from 'app/shared/metis/conversations/conversation.util';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

export enum ConversationDetailTabs {
    MEMBERS = 'members',
}

@Component({
    selector: 'jhi-conversation-detail-dialog',
    templateUrl: './conversation-detail-dialog.component.html',
})
export class ConversationDetailDialogComponent implements OnInit {
    public activeConversation: ConversationDto;
    @Input()
    set metisConversationService(metisConversationService: MetisConversationService) {
        this._metisConversationService = metisConversationService;
        this.course = this._metisConversationService.course!;
        this._metisConversationService.activeConversation$.subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }
    _metisConversationService: MetisConversationService;
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
            this._metisConversationService.forceRefresh().subscribe({
                complete: () => {
                    this.activeModal.close();
                },
            });
        } else {
            this.activeModal.dismiss();
        }
    }
}
