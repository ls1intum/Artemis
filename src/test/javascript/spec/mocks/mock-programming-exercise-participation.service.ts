import { of } from 'rxjs';
import { IProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/entities/result/result.model';

export class MockProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback = (participationId: number) => of({} as Result);
    getStudentParticipationWithLatestResult = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    checkIfParticipationHasResult = (participationId: number) => of(true);
}
