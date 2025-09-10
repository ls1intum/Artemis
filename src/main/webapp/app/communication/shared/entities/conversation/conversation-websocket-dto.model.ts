import { MetisPostAction } from 'app/communication/metis.util';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';

export class ConversationWebsocketDTO {
    public conversation: ConversationDTO;
    public action: MetisPostAction;
}
