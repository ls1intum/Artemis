import { of } from 'rxjs';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { RepositoryType } from '../../../../../../main/webapp/app/programming/shared/code-editor/model/code-editor.model';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithTemplateAndSolutionParticipation = (exerciseId: number) => of();
    findWithTemplateAndSolutionParticipationAndResults = (exerciseId: number) => of();
    findWithTemplateAndSolutionParticipationAndLatestResults = (exerciseId: number) => of();
    findWithAuxiliaryRepository = (programmingExerciseId: number) => of();
    find = (exerciseId: number) => of({ body: { id: 4 } });
    getProgrammingExerciseTestCaseState = (exerciseId: number) => of({ body: { released: true, hasStudentResult: true, testCasesChanged: false } });
    exportInstructorExercise = (exerciseId: number) => of({ body: undefined });
    exportInstructorRepository = (exerciseId: number, repositoryType: RepositoryType) => of({ body: undefined });
    exportStudentRepository = (exerciseId: number, participationId: number) => of({ body: undefined });
    exportStudentRequestedRepository = (exerciseId: number, includeTests: boolean) => of({ body: undefined });
    getTheiaConfig = (exerciseId: number) => of({});
    createStructuralSolutionEntries = (exerciseId: number) => of({});
    createBehavioralSolutionEntries = (exerciseId: number) => of({});
    getLatestResult = (participation: Participation) => of({});
    delete = (programmingExerciseId: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean) => of({});
    generateStructureOracle = (exerciseId: number) => of({});
    getCheckoutDirectoriesForProgrammingLanguage = (programmingLanguage: ProgrammingLanguage, checkoutSolution: boolean) => of();
    getTemplateRepositoryTestFilesWithContent = (exerciseId: number) => of(new Map<string, string>());
    getSolutionRepositoryTestFilesWithContent = (exerciseId: number) => of(new Map<string, string>());
}
