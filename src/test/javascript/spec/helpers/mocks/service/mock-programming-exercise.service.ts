import { of } from 'rxjs';
import { ProgrammingExerciseInstructorRepositoryType } from 'app/exercises/programming/manage/services/programming-exercise.service';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithTemplateAndSolutionParticipation = (exerciseId: number) => of();
    find = (exerciseId: number) => of({ body: { id: 4 } });
    getProgrammingExerciseTestCaseState = (exerciseId: number) => of({ body: { released: true, hasStudentResult: true, testCasesChanged: false } });
    exportInstructorRepository = (exerciseId: number, repositoryType: ProgrammingExerciseInstructorRepositoryType) => of({ body: undefined });
}
