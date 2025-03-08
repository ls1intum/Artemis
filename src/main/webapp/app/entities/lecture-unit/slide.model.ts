import { StringBaseEntity } from 'app/shared/model/base-entity';

export class Slide implements StringBaseEntity {
    public id?: string;
    public slideImagePath?: string;
    public slideNumber?: number;
    public hidden?: boolean;
}
