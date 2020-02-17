import { AgentParticipation } from 'app/entities/participation/agent-participation.model';
import { ProgrammingExerciseParticipation } from 'app/entities/participation/programming-exercise-participation.model';
import { Exercise } from 'app/entities/exercise';

export interface ProgrammingExerciseAgentParticipation extends ProgrammingExerciseParticipation, AgentParticipation {
    exercise: Exercise;
}
