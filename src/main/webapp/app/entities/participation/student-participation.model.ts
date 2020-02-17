import { User } from 'app/core/user/user.model';
import { Exercise } from '../exercise';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { AgentParticipation } from 'app/entities/participation/agent-participation.model';
import { Agent } from 'app/entities/participation/agent.model';

export class StudentParticipation extends Participation implements AgentParticipation {
    public student: User;
    public exercise: Exercise;

    constructor(type?: ParticipationType) {
        super(type ? type : ParticipationType.STUDENT);
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    getAgent(): User {
        return this.student;
    }

    setAgent(agent: Agent): void {
        this.student = agent as User;
    }
}
