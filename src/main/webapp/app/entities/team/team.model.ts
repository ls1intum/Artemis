import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';
import { User } from 'app/core/user/user.model';
import { Agent } from 'app/entities/participation/agent.model';

export class Team implements BaseEntity, Agent {
    public id: number;
    public name: string;
    public shortName: string;
    public image: string;
    public exercise: Exercise;
    public students: User[] = []; // default value

    constructor() {}

    public getName(): string {
        return this.name;
    }

    public getUsername(): string {
        return this.shortName;
    }

    public holds(agent: Agent): boolean {
        if (agent instanceof User) {
            return this.students.find(user => user.login === agent.login) !== undefined;
        } else if (agent instanceof Team) {
            return this.id === agent.id;
        } else {
            throw new Error('Unknown agent type.');
        }
    }
}
