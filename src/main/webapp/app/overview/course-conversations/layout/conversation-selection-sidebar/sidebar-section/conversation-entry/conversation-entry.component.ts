import { Component, Input, OnInit } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { faMessage } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: '[jhi-conversation-entry]',
    templateUrl: './conversation-entry.component.html',
    styleUrls: ['./conversation-entry.component.scss'],
})
export class ConversationEntryComponent implements OnInit {
    @Input()
    conversation: ConversationDto;

    @Input()
    isConversationUnread = false;

    @Input()
    isActive: boolean | undefined = false;

    faMessage = faMessage;
    constructor(public conversationService: ConversationService) {}

    getAsChannel = getAsChannelDto;
    getConversationName = this.conversationService.getConversationName;

    ngOnInit(): void {}
}
