import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { IExerciseHintService, ExerciseHintResponse } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

export class MockExerciseHintService implements IExerciseHintService {
    private exerciseHintDummy = { id: 1 } as ExerciseHint;
    private exerciseHintDummy2 = { id: 2 } as ExerciseHint;

    create(exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }

    delete(exerciseId: number, hintId: number): Observable<HttpResponse<any>> {
        return of();
    }

    find(exerciseId: number, id: number): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }

    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return of({ body: [this.exerciseHintDummy, this.exerciseHintDummy2] }) as Observable<HttpResponse<ExerciseHint[]>>;
    }

    update(exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }
}
