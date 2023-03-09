import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { MetisPostAction } from 'app/shared/metis/metis.util';

export class ConversationWebsocketDTO {
    public conversation: ConversationDto;
    public crudAction: MetisPostAction;
}
