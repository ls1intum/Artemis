import { MetisPostAction } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export class ConversationWebsocketDTO {
    public conversation: Conversation;
    public crudAction: MetisPostAction;
}
