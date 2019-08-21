import { of } from 'rxjs';
import { IProgrammingExerciseParticipationService } from 'app/entities/programming-exercise';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation';
import { Result } from 'app/entities/result';

export class MockProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback = (participationId: number) => of({} as Result);
    getStudentParticipationWithLatestResult = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    checkIfParticipationHasResult = (participationId: number) => of(true);
}
