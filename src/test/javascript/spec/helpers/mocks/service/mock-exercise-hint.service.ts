import { HttpResponse } from '@angular/common/http';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintResponse, IExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { Observable, of } from 'rxjs';

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

    findByExerciseId(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return of({ body: [this.exerciseHintDummy, this.exerciseHintDummy2] }) as Observable<HttpResponse<ExerciseHint[]>>;
    }

    update(exerciseId: number, exerciseHint: ExerciseHint): Observable<ExerciseHintResponse> {
        return of({ body: this.exerciseHintDummy }) as Observable<ExerciseHintResponse>;
    }

    activateExerciseHint(exerciseId: number, exerciseHintId: number): Observable<ExerciseHintResponse> {
        return of();
    }

    getActivatedExerciseHints(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return of();
    }

    getAvailableExerciseHints(exerciseId: number): Observable<HttpResponse<ExerciseHint[]>> {
        return of();
    }

    getTitle(exerciseId: number, exerciseHintId: number): Observable<HttpResponse<string>> {
        return of();
    }

    rateExerciseHint(exerciseId: number, exerciseHintId: number, ratingValue: number): Observable<HttpResponse<void>> {
        return of();
    }
}
