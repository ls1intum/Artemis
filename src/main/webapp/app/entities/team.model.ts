import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class Team implements BaseEntity {
    public id: number;
    public name: string;
    public shortName: string;
    public image?: string;
    public students: User[] = []; // default value

    constructor() {}
}
