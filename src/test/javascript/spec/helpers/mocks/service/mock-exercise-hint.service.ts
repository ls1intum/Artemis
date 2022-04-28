import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { IExerciseHintService, ExerciseHintResponse } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

export class MockExerciseHintService implements IExerciseHintService {
    private exerciseHintDummy = { id: 1 } as ExerciseHint;
    private exerciseHintDummy2 = { id: 2 } as ExerciseHint;

    create(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }

    delete(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<void>> {
        return of();
    }

    find(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }

    findByExerciseIdWithRelations(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return of({ body: [this.exerciseHintDummy, this.exerciseHintDummy2] }) as Observable<HttpResponse<ExerciseHint[]>>;
    }

    update(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }
}
