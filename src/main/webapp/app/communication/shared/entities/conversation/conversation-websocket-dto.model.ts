import { CommunicationCrudAction } from 'app/communication/communication.util';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ConversationWebsocketDTO {
    public conversation!: ConversationDTO;
    public action!: CommunicationCrudAction;
}
