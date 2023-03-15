import { Observable, of } from 'rxjs';

import { IProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';

export class MockProgrammingExerciseWebsocketService implements IProgrammingExerciseWebsocketService {
    getTestCaseState(programmingExerciseId: number): Observable<boolean> {
        return of(false);
    }
}
