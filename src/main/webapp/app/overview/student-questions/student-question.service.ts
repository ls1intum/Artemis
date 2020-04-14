import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { StudentQuestion } from 'app/entities/student-question.model';

type EntityResponseType = HttpResponse<StudentQuestion>;
type EntityArrayResponseType = HttpResponse<StudentQuestion[]>;

@Injectable({ providedIn: 'root' })
export class StudentQuestionService {
    public resourceUrl = SERVER_API_URL + 'api/student-questions';

    constructor(protected http: HttpClient) {}

    create(studentQuestion: StudentQuestion): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestion);
        return this.http
            .post<StudentQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(studentQuestion: StudentQuestion): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestion);
        return this.http
            .put<StudentQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    findQuestionsForExercise(exerciseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<StudentQuestion[]>(`api/exercises/${exerciseId}/student-questions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    findQuestionsForLecture(lectureId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<StudentQuestion[]>(`api/lectures/${lectureId}/student-questions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(studentQuestionId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${studentQuestionId}`, { observe: 'response' });
    }

    /**
     * Takes a studentQuestion and converts the date from the client
     * @param   {StudentQuestion} studentQuestion
     * @return  {StudentQuestion}
     */
    protected convertDateFromClient(studentQuestion: StudentQuestion): StudentQuestion {
        const copy: StudentQuestion = Object.assign({}, studentQuestion, {
            creationDate: studentQuestion.creationDate !== null && moment(studentQuestion.creationDate).isValid() ? moment(studentQuestion.creationDate).toJSON() : null,
        });
        return copy;
    }

    /**
     * Takes a studentQuestion and converts the date from the server
     * @param   {StudentQuestion} studentQuestion
     * @return  {StudentQuestion}
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.creationDate = res.body.creationDate !== null ? moment(res.body.creationDate) : null;
        }
        return res;
    }

    /**
     * Takes an array of studentQuestions and converts the date from the server
     * @param   {EntityArrayResponseType} res
     * @return  {EntityArrayResponseType}
     */
    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((studentQuestion: StudentQuestion) => {
                studentQuestion.creationDate = studentQuestion.creationDate !== null ? moment(studentQuestion.creationDate) : null;
            });
        }
        return res;
    }
}
