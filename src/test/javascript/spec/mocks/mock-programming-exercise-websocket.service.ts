import { IProgrammingExerciseWebsocketService } from 'app/entities/programming-exercise/services/programming-exercise-websocket.service';
import { of, Observable } from 'rxjs';

export class MockProgrammingExerciseWebsocketService implements IProgrammingExerciseWebsocketService {
    getTestCaseState(programmingExerciseId: number): Observable<boolean> {
        return of(false);
    }
}
