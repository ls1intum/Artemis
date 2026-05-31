import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercise/services/exercise.service';
import { ReachabilityReport } from 'app/math/shared/entities/hint-suggestion.model';

export type EntityResponseType = HttpResponse<MathExercise>;
export type EntityArrayResponseType = HttpResponse<MathExercise[]>;

@Injectable({ providedIn: 'root' })
export class MathExerciseService implements ExerciseServicable<MathExercise> {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);

    private resourceUrl = 'api/math/math-exercises';

    create(mathExercise: MathExercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(mathExercise);
        Object.assign(copy, { courseId: mathExercise.course?.id });
        return this.http
            .post<MathExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    update(mathExercise: MathExercise, req?: any): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(mathExercise);
        Object.assign(copy, { courseId: mathExercise.course?.id });
        return this.http
            .put<MathExercise>(this.resourceUrl, copy, { params: req, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    reevaluateAndUpdate(mathExercise: MathExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = ExerciseService.convertExerciseFromClient(mathExercise);
        Object.assign(copy, { courseId: mathExercise.course?.id });
        return this.http
            .put<MathExercise>(`${this.resourceUrl}/${mathExercise.id}/re-evaluate`, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<MathExercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<MathExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    delete(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    import(adaptedSourceMathExercise: MathExercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(adaptedSourceMathExercise);
        return this.http
            .post<MathExercise>(`${this.resourceUrl}/import/${adaptedSourceMathExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /** Runs the configured grader's reachability check; 404 if the grader does not support the check. */
    verifyReachability(exerciseId: number): Observable<ReachabilityReport | undefined> {
        return this.http.get<ReachabilityReport>(`${this.resourceUrl}/${exerciseId}/verify-reachability`, { observe: 'response' }).pipe(map((res) => res.body ?? undefined));
    }
}
