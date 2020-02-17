import { Team } from 'app/entities/team/team.model';
import { Exercise } from '../exercise';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { AgentParticipation } from 'app/entities/participation/agent-participation.model';
import { Agent } from 'app/entities/participation/agent.model';

export class TeamParticipation extends Participation implements AgentParticipation {
    public team: Team;
    public exercise: Exercise;

    constructor(type?: ParticipationType) {
        super(type ? type : ParticipationType.TEAM);
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    getAgent(): Team {
        return this.team;
    }

    setAgent(agent: Agent): void {
        this.team = agent as Team;
    }
}
