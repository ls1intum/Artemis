import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import dayjs from 'dayjs/esm';
import { IrisMessageContent } from 'app/entities/iris/iris-message-content.model';
import { IrisMessageSender } from 'app/entities/iris/iris-message-sender.model';
export class IrisMessage implements BaseEntity {
    public id?: number;
    public session?: IrisSession;
    public sendAt?: dayjs.Dayjs;
    public helpful?: boolean;
    public sender?: IrisMessageSender;
    public content?: IrisMessageContent[];
}
