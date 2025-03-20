import { BaseEntity } from 'app/shared/model/base-entity';

export class Slide implements BaseEntity {
    public id?: number;
    public slideImagePath?: string;
    public slideNumber?: number;
    public hidden?: boolean;
}
