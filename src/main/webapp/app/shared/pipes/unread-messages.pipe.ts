import { Pipe, PipeTransform } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { MessagingService } from 'app/shared/metis/messaging.service';

@Pipe({
    name: 'unreadMessages',
})
export class UnreadMessagesPipe implements PipeTransform {
    constructor(private courseMessagesService: MessagingService) {}
    transform(conversation: Conversation): number {
        const conversationParticipant = conversation.conversationParticipants!.find(
            (conversationParticipants) => conversationParticipants.user.id === this.courseMessagesService.userId,
        )!;
        return conversationParticipant.unreadMessagesCount!;
    }
}
