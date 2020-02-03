import { IProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming-submission/programming-submission.service';
import { of } from 'rxjs';

export class MockProgrammingSubmissionService implements IProgrammingSubmissionService {
    getLatestPendingSubmissionByParticipationId = (participationId: number) => of([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null] as ProgrammingSubmissionStateObj);
    getSubmissionStateOfExercise = (exerciseId: number) => of(null);
    triggerBuild = (participationId: number) => of({});
    triggerInstructorBuild = (participationId: number) => of({});
    unsubscribeAllWebsocketTopics = (exerciseId: number) => of(null);
    unsubscribeForLatestSubmissionOfParticipation = (participationId: number) => of(null);
}
