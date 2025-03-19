import { IProgrammingExerciseWebsocketService } from 'app/programming/manage/services/programming-exercise-websocket.service';
import { Observable, of } from 'rxjs';

export class MockProgrammingExerciseWebsocketService implements IProgrammingExerciseWebsocketService {
    getTestCaseState(programmingExerciseId: number): Observable<boolean> {
        return of(false);
    }
}
