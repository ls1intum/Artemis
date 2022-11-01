import { Component, Input, OnInit } from '@angular/core';
import { faChevronDown, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationService } from 'app/shared/metis/conversation.service';
import { ConversationType } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
})
export class ConversationHeaderComponent implements OnInit {
    readonly CHANNEL = ConversationType.CHANNEL;
    @Input()
    activeConversation: Conversation;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;
    faChevronDown = faChevronDown;

    constructor(public conversationService: ConversationService) {}

    ngOnInit(): void {}
}
