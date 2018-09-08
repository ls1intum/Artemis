import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IModelingExercise } from 'app/shared/model/modeling-exercise.model';

type EntityResponseType = HttpResponse<IModelingExercise>;
type EntityArrayResponseType = HttpResponse<IModelingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ModelingExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(private http: HttpClient) {}

    create(modelingExercise: IModelingExercise): Observable<EntityResponseType> {
        return this.http.post<IModelingExercise>(this.resourceUrl, modelingExercise, { observe: 'response' });
    }

    update(modelingExercise: IModelingExercise): Observable<EntityResponseType> {
        return this.http.put<IModelingExercise>(this.resourceUrl, modelingExercise, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IModelingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IModelingExercise[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
