import { MetisPostAction } from 'app/shared/metis/metis.util';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';

export class ConversationWebsocketDTO {
    public conversation: ConversationDto;
    public crudAction: MetisPostAction;
}
