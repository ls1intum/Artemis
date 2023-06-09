import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs';

export class DataExport implements BaseEntity {
    id?: number;
    dataExportState?: DataExportState;
    requestDate: dayjs.Dayjs;
    creationDate: dayjs.Dayjs;
    downloadDate: dayjs.Dayjs;
    user?: User;
}

export enum DataExportState {
    REQUESTED = 'REQUESTED',
    IN_CREATION = 'IN_CREATION',
    EMAIL_SENT = 'EMAIL_SENT',
    DOWNLOADED = 'DOWNLOADED',
    DELETED = 'DELETED',
    DOWNLOADED_DELETED = 'DOWNLOADED_DELETED',
    FAILED = 'FAILED',
}
