import { of } from 'rxjs';
import { IProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/entities/result.model';
import { CommitInfo } from 'app/entities/programming/programming-submission.model';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';

export class MockProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback = (participationId: number, withSubmission: boolean) => of({} as Result);
    getStudentParticipationWithLatestResult = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    getStudentParticipationWithAllResults = (participationId: number) => of({} as ProgrammingExerciseStudentParticipation);
    retrieveCommitHistoryForParticipation = (participationId: number) => of([] as CommitInfo[]);
    retrieveCommitHistoryForTemplateSolutionOrTests = (participationId: number, repositoryType: string) => of([] as CommitInfo[]);
    retrieveCommitHistoryForAuxiliaryRepository = (exerciseId: number, repositoryType: string, repositoryId: number) => of([] as CommitInfo[]);
    getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView = (exerciseId: number, participationId: number, commitId: string, repositoryType: string) =>
        of(new Map<string, string>());
    checkIfParticipationHasResult = (participationId: number) => of(true);
    getVcsAccessLogForRepository = (exerciseId: number, repositoryType: string) => of([] as VcsAccessLogDTO[]);
    getVcsAccessLogForParticipation = (participationId: number) => of([] as VcsAccessLogDTO[]);
}
