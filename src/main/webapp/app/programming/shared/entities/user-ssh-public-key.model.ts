import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class UserSshPublicKey implements BaseEntity {
    id!: number;
    label!: string;
    publicKey!: string;
    keyHash!: string;
    expiryDate?: dayjs.Dayjs;
    lastUsedDate?: dayjs.Dayjs;
    creationDate!: dayjs.Dayjs;
    hasExpired?: boolean;
}
