import { MetisPostAction } from 'app/shared/metis/metis.util';
import { ChatSession } from './chat-session.model';

export class ChatSessionDTO {
    public chatSession: ChatSession;
    public crudAction: MetisPostAction;
}
