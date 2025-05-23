import { IProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { EMPTY, Observable, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export class MockProgrammingSubmissionService implements IProgrammingSubmissionService {
    getLatestPendingSubmissionByParticipationId = (participationId: number) =>
        of([1, ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null] as unknown as ProgrammingSubmissionStateObj);
    getSubmissionStateOfExercise = (exerciseId: number) => EMPTY;
    triggerBuild = (participationId: number) => of({});
    triggerInstructorBuild = (participationId: number) => of({});
    unsubscribeAllWebsocketTopics = (exercise: Exercise) => of(null);
    unsubscribeForLatestSubmissionOfParticipation = (participationId: number) => of(null);
    getResultEtaInMs: () => Observable<number>;
    triggerInstructorBuildForAllParticipationsOfExercise: (exerciseId: number) => Observable<void>;
    triggerInstructorBuildForParticipationsOfExercise: (exerciseId: number, participationIds: number[]) => Observable<void>;
    getIsLocalCIProfile = () => false;
    fetchQueueReleaseDateEstimationByParticipationId: (participationId: number) => Observable<dayjs.Dayjs | undefined> = () => of(undefined);
}
