import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';
import { User } from 'app/core/user/user.model';
import { Agent } from 'app/entities/participation/agent.model';

export class Team implements BaseEntity, Agent {
    public id: number;
    public name: string;
    public shortName: string;
    public username: string;
    public image: string;
    public exercise: Exercise;
    public students: User[] = []; // default value

    constructor() {}
}
