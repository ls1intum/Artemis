import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { QuizExercise } from './quiz-exercise.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<QuizExercise>;

@Injectable()
export class QuizExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/quiz-exercises';

    constructor(private http: HttpClient) { }

    create(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.convert(quizExercise);
        return this.http.post<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.convert(quizExercise);
        return this.http.put<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<QuizExercise>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<QuizExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<QuizExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<QuizExercise[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: QuizExercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<QuizExercise[]>): HttpResponse<QuizExercise[]> {
        const jsonResponse: QuizExercise[] = res.body;
        const body: QuizExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to QuizExercise.
     */
    private convertItemFromServer(quizExercise: QuizExercise): QuizExercise {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        return copy;
    }

    /**
     * Convert a QuizExercise to a JSON which can be sent to the server.
     */
    private convert(quizExercise: QuizExercise): QuizExercise {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        return copy;
    }
}
