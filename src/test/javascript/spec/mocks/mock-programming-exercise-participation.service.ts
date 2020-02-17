import { of } from 'rxjs';
import { IProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ProgrammingExerciseAgentParticipation } from 'app/entities/participation';
import { Result } from 'app/entities/result';

export class MockProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback = (participationId: number) => of({} as Result);
    getAgentParticipationWithLatestResult = (participationId: number) => of({} as ProgrammingExerciseAgentParticipation);
    checkIfParticipationHasResult = (participationId: number) => of(true);
}
