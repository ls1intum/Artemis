import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';

export class UserSshPublicKey implements BaseEntity {
    id: number;
    label: string;
    publicKey: string;
    keyHash: string;
    expiryDate?: dayjs.Dayjs;
    lastUsedDate?: dayjs.Dayjs;
    creationDate: dayjs.Dayjs;
    hasExpired?: boolean;
}
