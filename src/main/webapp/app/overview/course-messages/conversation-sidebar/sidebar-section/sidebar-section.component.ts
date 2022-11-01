import { Component, ContentChild, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { faChevronRight, faHashtag, faLock, faMessage } from '@fortawesome/free-solid-svg-icons';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationType } from 'app/shared/metis/metis.util';
import { MessagingService } from 'app/shared/metis/messaging.service';
import { ConversationService } from 'app/shared/metis/conversation.service';

@Component({
    selector: 'jhi-sidebar-section',
    templateUrl: './sidebar-section.component.html',
    styleUrls: ['./sidebar-section.component.scss'],
})
export class SidebarSectionComponent {
    @Output() conversationSelected = new EventEmitter<Conversation>();

    @Input()
    label: string;

    @Input()
    activeConversation?: Conversation;

    @Input()
    conversations: Conversation[] = [];

    @Input()
    isCollapsed = false;

    isHover = false;

    @ContentChild(TemplateRef) sectionButtons: TemplateRef<any>;

    // icon imports
    faChevronRight = faChevronRight;
    faMessage = faMessage;

    constructor(protected courseMessagesService: MessagingService, public conversationService: ConversationService) {}

    conversationsTrackByFn = (index: number, conversation: Conversation): number => conversation.id!;

    isConversationUnread(conversation: Conversation): boolean {
        const conversationParticipant = conversation.conversationParticipants!.find(
            (conversationParticipants) => conversationParticipants.user.id === this.courseMessagesService.userId,
        )!;

        if (this.activeConversation && conversation.id !== this.activeConversation.id && !!conversation.lastMessageDate && !!conversationParticipant.lastRead) {
            if (conversationParticipant.lastRead.isBefore(conversation.lastMessageDate.subtract(1, 'second'), 'second')) {
                return true;
            }
        }
        return false;
    }
}
