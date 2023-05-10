import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisSession } from 'app/entities/iris/session.model';
import dayjs from 'dayjs/esm';
import { IrisMessageContent } from 'app/entities/iris/content.model';
import { IrisMessageSender } from 'app/entities/iris/sender.model';
export class IrisMessage implements BaseEntity {
    public id?: number;
    public session?: IrisSession;
    public sendAt?: dayjs.Dayjs;
    public helpful?: boolean;
    public sender?: IrisMessageSender;
    public content?: IrisMessageContent[];
}
