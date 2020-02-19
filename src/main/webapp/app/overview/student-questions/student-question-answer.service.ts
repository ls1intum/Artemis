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

    create(studentQuestionAnswer: StudentQuestionAnswer): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestionAnswer);
        return this.http
            .post<StudentQuestionAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(studentQuestionAnswer: StudentQuestionAnswer): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestionAnswer);
        return this.http
            .put<StudentQuestionAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentQuestionAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<StudentQuestionAnswer[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    protected convertDateFromClient(studentQuestionAnswer: StudentQuestionAnswer): StudentQuestionAnswer {
        const copy: StudentQuestionAnswer = Object.assign({}, studentQuestionAnswer, {
            answerDate: studentQuestionAnswer.answerDate != null && moment(studentQuestionAnswer.answerDate).isValid() ? moment(studentQuestionAnswer.answerDate).toJSON() : null,
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.answerDate = res.body.answerDate != null ? moment(res.body.answerDate) : null;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((studentQuestionAnswer: StudentQuestionAnswer) => {
                studentQuestionAnswer.answerDate = studentQuestionAnswer.answerDate != null ? moment(studentQuestionAnswer.answerDate) : null;
            });
        }
        return res;
    }
}
