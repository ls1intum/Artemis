import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/entities/exercise.model';

export class Team implements BaseEntity {
    public id: number;
    public name: string;
    public shortName: string;
    public image: string;
    public exercise: Exercise;
    public students: User[] = []; // default value

    constructor() {}
}
