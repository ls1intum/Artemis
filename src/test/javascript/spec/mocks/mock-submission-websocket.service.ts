import { ISubmissionWebsocketService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/submission/programming-submission-websocket.service';
import { Observable, of } from 'rxjs';
import { Submission } from 'app/entities/submission';

export class MockSubmissionWebsocketService implements ISubmissionWebsocketService {
    getLatestPendingSubmission = (participationId: number) => of([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null] as ProgrammingSubmissionStateObj);
    triggerBuild = (participationId: number) => of({});
    triggerInstructorBuild = (participationId: number) => of({});
}
