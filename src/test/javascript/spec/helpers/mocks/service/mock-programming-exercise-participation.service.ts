import { of } from 'rxjs';
import { IProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { VcsAccessLogDTO } from 'app/programming/shared/entities/vcs-access-log-entry.model';

export class MockProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback = (participationId: number) => of({} as Result);
    getStudentParticipationWithLatestResult = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    getStudentParticipationWithAllResults = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    retrieveCommitHistoryForParticipation = (participationId: number) => of([] as CommitInfo[]);
    retrieveCommitHistoryForTemplateSolutionOrTests = (participationId: number, repositoryType: string) => of([] as CommitInfo[]);
    retrieveCommitHistoryForAuxiliaryRepository = (exerciseId: number, repositoryId: number) => of([] as CommitInfo[]);
    getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView = (exerciseId: number, participationId: number, commitId: string, repositoryType: string) =>
        of(new Map<string, string>());
    checkIfParticipationHasResult = (participationId: number) => of(true);
    getVcsAccessLogForRepository = (exerciseId: number, repositoryType: string) => of([] as VcsAccessLogDTO[]);
    getVcsAccessLogForParticipation = (participationId: number) => of([] as VcsAccessLogDTO[]);
}
