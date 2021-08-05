import { of, Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';

export class MockExerciseService {
    find(exerciseId: number) {
        return MockExerciseService.response({ id: exerciseId } as Exercise);
    }

    getUpcomingExercises() {
        return MockExerciseService.response([{ id: 1 } as Exercise, { id: 2 } as Exercise]);
    }

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }

    convertExerciseForServer<E extends Exercise>(exercise: E): Exercise {
        return exercise;
    }
}
