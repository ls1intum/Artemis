import { MetisPostAction } from 'app/shared/metis/metis.util';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';

export class ConversationWebsocketDTO {
    public conversation: ConversationDTO;
    public action: MetisPostAction;
}
