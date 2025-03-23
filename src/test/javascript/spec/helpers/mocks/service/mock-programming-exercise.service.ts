import { of } from 'rxjs';
import { Participation } from 'app/exercise/entities/participation/participation.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
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
    getDiffReport = (exerciseId: number) => of({});
    getBuildLogStatistics = (exerciseId: number) => of({});
    getLatestResult = (participation: Participation) => of({});
    combineTemplateRepositoryCommits = (exerciseId: number) => of({});
    delete = (programmingExerciseId: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean) => of({});
    generateStructureOracle = (exerciseId: number) => of({});
    getDiffReportForCommits = (exerciseId: number, participationId: number, olderCommitHash: string, newerCommitHash: string, repositoryType: string) => of({});
    getCheckoutDirectoriesForProgrammingLanguage = (programmingLanguage: ProgrammingLanguage, checkoutSolution: boolean) => of();
}
