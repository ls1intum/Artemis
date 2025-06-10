import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export class IrisSessionDto implements BaseEntity {
    id: number;
    userId: number;
    messages?: IrisMessage[];
    creationDate: Date;
    chatMode: ChatServiceMode;
    entityId: number;

    static parseChatMode(mode: string): ChatServiceMode | undefined {
        return Object.values(ChatServiceMode).includes(mode as ChatServiceMode) ? (mode as ChatServiceMode) : undefined;
    }

    constructor(init?: Partial<IrisSessionDto>) {
        if (init) {
            Object.assign(this, init);
            if (typeof init.chatMode === 'string') {
                this.chatMode = IrisSessionDto.parseChatMode(init.chatMode) as ChatServiceMode;
            }
        }
    }
}
