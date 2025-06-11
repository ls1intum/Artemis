import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export class IrisSession implements BaseEntity {
    id: number;
    user?: User;
    messages?: IrisMessage[];
    latestSuggestions?: string;
    creationDate: Date;
    chatMode: ChatServiceMode;
    entityId: number;
    type?: string;

    static parseChatMode(mode: string): ChatServiceMode | undefined {
        const parsedMode = mode.replaceAll('_', '-');
        return Object.values(ChatServiceMode).includes(parsedMode as ChatServiceMode) ? (parsedMode as ChatServiceMode) : undefined;
    }

    constructor(init?: Partial<IrisSession>) {
        if (init) {
            Object.assign(this, init);
            if (typeof init.type === 'string') {
                this.chatMode = IrisSession.parseChatMode(init.type) as ChatServiceMode;
            }
        }
    }
}
