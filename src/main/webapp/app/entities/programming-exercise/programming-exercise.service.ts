import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IProgrammingExercise } from 'app/shared/model/programming-exercise.model';

type EntityResponseType = HttpResponse<IProgrammingExercise>;
type EntityArrayResponseType = HttpResponse<IProgrammingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient) {}

    create(programmingExercise: IProgrammingExercise): Observable<EntityResponseType> {
        return this.http.post<IProgrammingExercise>(this.resourceUrl, programmingExercise, { observe: 'response' });
    }

    update(programmingExercise: IProgrammingExercise): Observable<EntityResponseType> {
        return this.http.put<IProgrammingExercise>(this.resourceUrl, programmingExercise, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IProgrammingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IProgrammingExercise[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
