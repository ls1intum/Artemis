import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercise/services/exercise.service';

export type EntityResponseType = HttpResponse<ProofExercise>;
export type EntityArrayResponseType = HttpResponse<ProofExercise[]>;

@Injectable({ providedIn: 'root' })
export class ProofExerciseService implements ExerciseServicable<ProofExercise> {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);

    private resourceUrl = 'api/proof/proof-exercises';

    create(proofExercise: ProofExercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(proofExercise);
        Object.assign(copy, { courseId: proofExercise.course?.id });
        return this.http
            .post<ProofExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    update(proofExercise: ProofExercise, req?: any): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(proofExercise);
        Object.assign(copy, { courseId: proofExercise.course?.id });
        return this.http
            .put<ProofExercise>(this.resourceUrl, copy, { params: req, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    reevaluateAndUpdate(proofExercise: ProofExercise, req?: any): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(proofExercise);
        return this.http
            .put<ProofExercise>(`${this.resourceUrl}/reevaluate`, copy, { params: req, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ProofExercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ProofExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    delete(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    import(adaptedSourceProofExercise: ProofExercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseFromClient(adaptedSourceProofExercise);
        return this.http
            .post<ProofExercise>(`${this.resourceUrl}/import/${adaptedSourceProofExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }
}
