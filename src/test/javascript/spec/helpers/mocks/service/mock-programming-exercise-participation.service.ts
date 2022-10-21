import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/entities/result.model';
import { IProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { of } from 'rxjs';

export class MockProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback = (participationId: number, withSubmission: boolean) => of({} as Result);
    getStudentParticipationWithLatestResult = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    checkIfParticipationHasResult = (participationId: number) => of(true);
}
