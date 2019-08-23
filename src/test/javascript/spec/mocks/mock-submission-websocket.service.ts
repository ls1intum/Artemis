import { ISubmissionWebsocketService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/submission/programming-submission-websocket.service';
import { of } from 'rxjs';

export class MockSubmissionWebsocketService implements ISubmissionWebsocketService {
    getLatestPendingSubmissionByParticipationId = (participationId: number) => of([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null] as ProgrammingSubmissionStateObj);
    triggerBuild = (participationId: number) => of({});
    triggerInstructorBuild = (participationId: number) => of({});
}
