import { of } from 'rxjs';
import { ProgrammingExerciseInstructorRepositoryType } from 'app/exercises/programming/manage/services/programming-exercise.service';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithTemplateAndSolutionParticipation = (exerciseId: number) => of();
    findWithTemplateAndSolutionParticipationAndResults = (exerciseId: number) => of();
    find = (exerciseId: number) => of({ body: { id: 4 } });
    getProgrammingExerciseTestCaseState = (exerciseId: number) => of({ body: { released: true, hasStudentResult: true, testCasesChanged: false } });
    exportInstructorExercise = (exerciseId: number) => of({ body: undefined });
    exportInstructorRepository = (exerciseId: number, repositoryType: ProgrammingExerciseInstructorRepositoryType) => of({ body: undefined });
    getTasksAndTestsExtractedFromProblemStatement = (exerciseId: number) => of();
    deleteTasksWithSolutionEntries = (exerciseId: number) => of();
    getDiffReport = (exerciseId: number) => of({});
    createStructuralSolutionEntries = (exerciseId: number) => of({});
    createBehavioralSolutionEntries = (exerciseId: number) => of({});
}
