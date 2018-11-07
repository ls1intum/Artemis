import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { QuizExercise } from './quiz-exercise.model';
import { createRequestOption } from 'app/shared';
import { ExerciseService } from 'app/entities/exercise';

export type EntityResponseType = HttpResponse<QuizExercise>;
export type EntityArrayResponseType = HttpResponse<QuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(quizExercise);
        return this.http
            .post<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    update(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(quizExercise);
        return this.http
            .put<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    recalculate(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${id}/recalculate-statistics`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    findForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(`api/courses/${courseId}/quiz-exercises`, { observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    openForPractice(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/open-for-practice`, { observe: 'response' });
    }

    findForStudent(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${id}/for-student`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    start(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/start-now`, { observe: 'response' });
    }

    setVisible(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/set-visible`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<QuizExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    releaseStatistics(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/release-statistics`, { observe: 'response' });
    }

    revokeStatistics(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/revoke-statistics`, { observe: 'response' });
    }
}
