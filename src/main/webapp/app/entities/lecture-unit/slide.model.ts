import { StringBaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/entities/exercise.model';

export class Slide implements StringBaseEntity {
    public id?: string;
    public slideImagePath?: string;
    public slideNumber?: number;
    public hidden?: Date;
    public exercise?: Exercise;
}
