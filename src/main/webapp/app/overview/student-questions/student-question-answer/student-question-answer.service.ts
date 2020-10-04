import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';

type EntityResponseType = HttpResponse<StudentQuestionAnswer>;
type EntityArrayResponseType = HttpResponse<StudentQuestionAnswer[]>;

@Injectable({ providedIn: 'root' })
export class StudentQuestionAnswerService {
    public resourceUrl = SERVER_API_URL + 'api/student-question-answers';

    constructor(protected http: HttpClient) {}

    /**
     * create studentQuestionAnswer
     * @param {StudentQuestionAnswer} studentQuestionAnswer
     * @return {Observable<EntityResponseType>}
     */
    create(studentQuestionAnswer: StudentQuestionAnswer): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestionAnswer);
        return this.http
            .post<StudentQuestionAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update studentQuestionAnswer
     * @param {StudentQuestionAnswer} studentQuestionAnswer
     * @return {Observable<EntityResponseType>}
     */
    update(studentQuestionAnswer: StudentQuestionAnswer): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestionAnswer);
        return this.http
            .put<StudentQuestionAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find studentQuestionAnswer by id
     * @param {number} id
     * @return {Observable<EntityResponseType>}
     */
    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentQuestionAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * get studentQuestionAnswers for query
     * @param {any} req?
     * @return Observable<EntityArrayResponseType>
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<StudentQuestionAnswer[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * delete studentQuestionAnswer by id
     * @param {number} id
     * @return {Observable<HttpResponse<any>>}
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * Takes a studentQuestionAnswer and converts the date from the client
     * @param   {StudentQuestionAnswer} studentQuestionAnswer
     * @return  {StudentQuestionAnswer}
     */
    protected convertDateFromClient(studentQuestionAnswer: StudentQuestionAnswer): StudentQuestionAnswer {
        const copy: StudentQuestionAnswer = Object.assign({}, studentQuestionAnswer, {
            answerDate: studentQuestionAnswer.answerDate && moment(studentQuestionAnswer.answerDate).isValid() ? moment(studentQuestionAnswer.answerDate).toJSON() : undefined,
        });
        return copy;
    }

    /**
     * Takes a studentQuestionAnswer and converts the date from the server
     * @param   {StudentQuestionAnswer} studentQuestionAnswer
     * @return  {StudentQuestionAnswer}
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.answerDate = res.body.answerDate ? moment(res.body.answerDate) : undefined;
        }
        return res;
    }

    /**
     * Takes an array of studentQuestionAnswers and converts the date from the server
     * @param   {EntityArrayResponseType} res
     * @return  {EntityArrayResponseType}
     */
    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((studentQuestionAnswer: StudentQuestionAnswer) => {
                studentQuestionAnswer.answerDate = studentQuestionAnswer.answerDate ? moment(studentQuestionAnswer.answerDate) : undefined;
            });
        }
        return res;
    }
}
