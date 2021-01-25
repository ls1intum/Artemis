import { of } from 'rxjs';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithTemplateAndSolutionParticipation = (exerciseId: number) => of();
    findWithTemplateAndSolutionParticipationAndResults = (exerciseId: number) => of();
    find = (exerciseId: number) => of({ body: { id: 4 } });
    getProgrammingExerciseTestCaseState = (exerciseId: number) => of({ body: { released: true, hasStudentResult: true, testCasesChanged: false } });
}
