import { of } from 'rxjs';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithParticipations = (exerciseId: number) => of();
}
