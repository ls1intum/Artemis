import { Agent } from 'app/entities/participation/agent.model';
import { Exercise } from '../exercise';
import { Participation } from 'app/entities/participation/participation.model';

export interface AgentParticipation extends Participation {
    exercise: Exercise;

    getExercise(): Exercise;

    getAgent(): Agent;

    setAgent(agent: Agent): void;
}
