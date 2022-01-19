import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintResponse, IExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';

export class MockExerciseHintService implements IExerciseHintService {
    private exerciseHintDummy = { id: 1 } as ExerciseHint;
    private exerciseHintDummy2 = { id: 2 } as ExerciseHint;

    find(id: number): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }

    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return of({ body: [this.exerciseHintDummy, this.exerciseHintDummy2] }) as Observable<HttpResponse<ExerciseHint[]>>;
    }
}
