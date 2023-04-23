import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs';

export class DataExport implements BaseEntity {
    id?: number;
    requestDate: dayjs.Dayjs;
    creationDate: dayjs.Dayjs;
    downloadDate: dayjs.Dayjs;
    user?: User;
}
