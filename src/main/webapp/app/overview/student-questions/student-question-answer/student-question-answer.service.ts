import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';

type EntityResponseType = HttpResponse<StudentQuestionAnswer>;
type EntityArrayResponseType = HttpResponse<StudentQuestionAnswer[]>;

@Injectable({ providedIn: 'root' })
export class StudentQuestionAnswerService {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * create studentQuestionAnswer
     * @param {number} courseId
     * @param {StudentQuestionAnswer} studentQuestionAnswer
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, studentQuestionAnswer: StudentQuestionAnswer): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestionAnswer);
        return this.http
            .post<StudentQuestionAnswer>(`${this.resourceUrl}${courseId}/student-question-answers`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update studentQuestionAnswer
     * @param {number} courseId
     * @param {StudentQuestionAnswer} studentQuestionAnswer
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, studentQuestionAnswer: StudentQuestionAnswer): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestionAnswer);
        return this.http
            .put<StudentQuestionAnswer>(`${this.resourceUrl}${courseId}/student-question-answers`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find studentQuestionAnswer by id
     * @param {number} courseId
     * @param {number} id
     * @return {Observable<EntityResponseType>}
     */
    find(courseId: number, id: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentQuestionAnswer>(`${this.resourceUrl}${courseId}/student-question-answers/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * delete studentQuestionAnswer by id
     * @param {number} courseId
     * @param {number} id
     * @return {Observable<HttpResponse<any>>}
     */
    delete(courseId: number, id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}${courseId}/student-question-answers/${id}`, { observe: 'response' });
    }

    /**
     * Takes a studentQuestionAnswer and converts the date from the client
     * @param   {StudentQuestionAnswer} studentQuestionAnswer
     * @return  {StudentQuestionAnswer}
     */
    protected convertDateFromClient(studentQuestionAnswer: StudentQuestionAnswer): StudentQuestionAnswer {
        return Object.assign({}, studentQuestionAnswer, {
            answerDate: studentQuestionAnswer.answerDate && moment(studentQuestionAnswer.answerDate).isValid() ? moment(studentQuestionAnswer.answerDate).toJSON() : undefined,
        });
    }

    /**
     * Takes a studentQuestionAnswer and converts the date from the server
     * @param   {EntityResponseType} res
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
