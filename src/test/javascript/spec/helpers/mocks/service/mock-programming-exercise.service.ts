import { of } from 'rxjs';
import { ProgrammingExerciseInstructorRepositoryType } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Participation } from 'app/entities/participation/participation.model';

export class MockProgrammingExerciseService {
    updateProblemStatement = (exerciseId: number, problemStatement: string) => of();
    findWithTemplateAndSolutionParticipation = (exerciseId: number) => of();
    findWithTemplateAndSolutionParticipationAndResults = (exerciseId: number) => of();
    findWithTemplateAndSolutionParticipationAndLatestResults = (exerciseId: number) => of();
    find = (exerciseId: number) => of({ body: { id: 4 } });
    getProgrammingExerciseTestCaseState = (exerciseId: number) => of({ body: { released: true, hasStudentResult: true, testCasesChanged: false } });
    exportInstructorExercise = (exerciseId: number) => of({ body: undefined });
    exportInstructorRepository = (exerciseId: number, repositoryType: ProgrammingExerciseInstructorRepositoryType) => of({ body: undefined });
    exportStudentRequestedRepository = (exerciseId: number, includeTests: boolean) => of({ body: undefined });
    getTasksAndTestsExtractedFromProblemStatement = (exerciseId: number) => of();
    deleteTasksWithSolutionEntries = (exerciseId: number) => of();
    getDiffReport = (exerciseId: number) => of({});
    getBuildLogStatistics = (exerciseId: number) => of({});
    createStructuralSolutionEntries = (exerciseId: number) => of({});
    createBehavioralSolutionEntries = (exerciseId: number) => of({});
    getLatestResult = (participation: Participation) => of({});
    getLatestFullTestwiseCoverageReport = (exerciseId: number) => of({});
    combineTemplateRepositoryCommits = (exerciseId: number) => of({});
    delete = (programmingExerciseId: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean) => of({});
    generateStructureOracle = (exerciseId: number) => of({});
    unlockAllRepositories = (exerciseId: number) => of({});
    getDiffReportForCommits = (exerciseId: number, participationId: number, olderCommitHash: string, newerCommitHash: string, repositoryType: string) => of({});
}
