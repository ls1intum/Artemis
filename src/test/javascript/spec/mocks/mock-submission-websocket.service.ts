import { ISubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { Observable, of } from 'rxjs';
import { Submission } from 'app/entities/submission';

export class MockSubmissionWebsocketService implements ISubmissionWebsocketService {
    getLatestPendingSubmission = (participationId: number) => of(null);
}
