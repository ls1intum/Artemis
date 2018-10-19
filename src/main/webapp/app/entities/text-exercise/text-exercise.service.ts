import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ITextExercise } from 'app/shared/model/text-exercise.model';

type EntityResponseType = HttpResponse<ITextExercise>;
type EntityArrayResponseType = HttpResponse<ITextExercise[]>;

@Injectable({ providedIn: 'root' })
export class TextExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient) {}

    create(textExercise: ITextExercise): Observable<EntityResponseType> {
        return this.http.post<ITextExercise>(this.resourceUrl, textExercise, { observe: 'response' });
    }

    update(textExercise: ITextExercise): Observable<EntityResponseType> {
        return this.http.put<ITextExercise>(this.resourceUrl, textExercise, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ITextExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<ITextExercise[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
