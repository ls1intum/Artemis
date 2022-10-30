import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faChevronRight, faMessage } from '@fortawesome/free-solid-svg-icons';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationType } from 'app/shared/metis/metis.util';
import { MessagingService } from 'app/shared/metis/messaging.service';

@Component({
    selector: 'jhi-sidebar-section',
    templateUrl: './sidebar-section.component.html',
    styleUrls: ['./sidebar-section.component.scss'],
})
export class SidebarSectionComponent {
    @Output() conversationSelected = new EventEmitter<Conversation>();

    @Input()
    activeConversation: Conversation;

    @Input()
    conversations: Conversation[] = [];

    @Input()
    isCollapsed = false;

    // icon imports
    faChevronRight = faChevronRight;
    faMessage = faMessage;

    constructor(protected courseMessagesService: MessagingService) {}

    conversationsTrackByFn = (index: number, conversation: Conversation): number => conversation.id!;

    getNameOfConversation(conversation: Conversation): string {
        if (conversation.type === ConversationType.CHANNEL) {
            return '#' + (conversation.name ?? '');
        } else if (conversation.type === ConversationType.DIRECT) {
            const participant = conversation.conversationParticipants!.find(
                (conversationParticipants) => conversationParticipants.user.id !== this.courseMessagesService.userId,
            )!.user;
            return participant.lastName ? `${participant.firstName} ${participant.lastName}` : participant.firstName!;
        } else {
            return '';
        }
    }

    isConversationUnread(conversation: Conversation): boolean {
        const conversationParticipant = conversation.conversationParticipants!.find(
            (conversationParticipants) => conversationParticipants.user.id === this.courseMessagesService.userId,
        )!;

        if (conversation.id !== this.activeConversation.id && !!conversation.lastMessageDate && !!conversationParticipant.lastRead) {
            if (conversationParticipant.lastRead.isBefore(conversation.lastMessageDate.subtract(1, 'second'), 'second')) {
                return true;
            }
        }
        return false;
    }
}
