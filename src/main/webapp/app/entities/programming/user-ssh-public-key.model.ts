import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

export class UserSshPublicKey implements BaseEntity {
    id?: number;
    label: string;
    publicKey: string;
    keyHash: string;
    expiryDate?: dayjs.Dayjs;
}
