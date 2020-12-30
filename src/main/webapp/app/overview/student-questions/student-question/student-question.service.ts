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
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * create a studentQuestion
     * @param {number} courseId
     * @param {StudentQuestion} studentQuestion
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, studentQuestion: StudentQuestion): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestion);
        return this.http
            .post<StudentQuestion>(`${this.resourceUrl}${courseId}/student-questions`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update the studentQuestion
     * @param {number} courseId
     * @param {StudentQuestion} studentQuestion
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, studentQuestion: StudentQuestion): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestion);
        return this.http
            .put<StudentQuestion>(`${this.resourceUrl}${courseId}/student-questions`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update the votes of a studentQuestion
     * @param {number} courseId
     * @param {number} questionId
     * @param {number} voteChange
     * @return {Observable<EntityResponseType>}
     */
    updateVotes(courseId: number, questionId: number, voteChange: number): Observable<EntityResponseType> {
        return this.http
            .put(`${this.resourceUrl}${courseId}/student-questions/${questionId}/votes`, voteChange, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find all questions for id of course
     * @param {number} courseId
     * @return {Observable<EntityArrayResponseType>}
     */
    findQuestionsForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<StudentQuestion[]>(`api/courses/${courseId}/student-questions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * delete studentQuestion by id
     * @param {number} courseId
     * @param {number} studentQuestionId
     * @return {Observable<HttpResponse<any>>}
     */
    delete(courseId: number, studentQuestionId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}${courseId}/student-questions/${studentQuestionId}`, { observe: 'response' });
    }

    /**
     * Takes a studentQuestion and converts the date from the client
     * @param   {StudentQuestion} studentQuestion
     * @return  {StudentQuestion}
     */
    protected convertDateFromClient(studentQuestion: StudentQuestion): StudentQuestion {
        return Object.assign({}, studentQuestion, {
            creationDate: studentQuestion.creationDate && moment(studentQuestion.creationDate).isValid() ? moment(studentQuestion.creationDate).toJSON() : undefined,
        });
    }

    /**
     * Takes a studentQuestion and converts the date from the server
     * @param   {EntityResponseType} res
     * @return  {StudentQuestion}
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? moment(res.body.creationDate) : undefined;
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
                studentQuestion.creationDate = studentQuestion.creationDate ? moment(studentQuestion.creationDate) : undefined;
            });
        }
        return res;
    }
}
