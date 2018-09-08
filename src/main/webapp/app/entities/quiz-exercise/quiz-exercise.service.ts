import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';

type EntityResponseType = HttpResponse<IQuizExercise>;
type EntityArrayResponseType = HttpResponse<IQuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises';

    constructor(private http: HttpClient) {}

    create(quizExercise: IQuizExercise): Observable<EntityResponseType> {
        return this.http.post<IQuizExercise>(this.resourceUrl, quizExercise, { observe: 'response' });
    }

    update(quizExercise: IQuizExercise): Observable<EntityResponseType> {
        return this.http.put<IQuizExercise>(this.resourceUrl, quizExercise, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IQuizExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IQuizExercise[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
