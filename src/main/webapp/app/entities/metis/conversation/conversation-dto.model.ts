import { MetisPostAction } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export class ConversationDTO {
    public conversation: Conversation;
    public crudAction: MetisPostAction;
}
