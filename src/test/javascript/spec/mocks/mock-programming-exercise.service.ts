import { of } from 'rxjs';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithTemplateAndSolutionParticipation = (exerciseId: number) => of();
}
