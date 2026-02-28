import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';

export class DataExport implements BaseEntity {
    id?: number;
    dataExportState?: DataExportState;
    createdDate?: dayjs.Dayjs;
    creationFinishedDate?: dayjs.Dayjs;
    downloadDate?: dayjs.Dayjs;
    nextRequestDate?: dayjs.Dayjs;
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
    EMAIL_FAILED = 'EMAIL_FAILED',
}

/**
 * DTO for admin data export overview
 */
export interface AdminDataExport {
    id: number;
    userId?: number;
    userLogin?: string;
    userName?: string;
    dataExportState: DataExportState;
    createdDate?: dayjs.Dayjs;
    creationFinishedDate?: dayjs.Dayjs;
    downloadable: boolean;
}
