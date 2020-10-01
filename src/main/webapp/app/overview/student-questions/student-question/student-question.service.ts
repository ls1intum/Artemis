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

    /**
     * create a studentQuestion
     * @param {StudentQuestion} studentQuestion
     * @return {Observable<EntityResponseType>}
     */
    create(studentQuestion: StudentQuestion): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestion);
        return this.http
            .post<StudentQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update the studentQuestion
     * @param {StudentQuestion} studentQuestion
     * @return {Observable<EntityResponseType>}
     */
    update(studentQuestion: StudentQuestion): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(studentQuestion);
        return this.http
            .put<StudentQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update the votes of a studentQuestion
     * @param {number} questionId
     * @param {number} votes
     * @return {Observable<EntityResponseType>}
     */
    updateVotes(questionId: number, voteChange: number): Observable<EntityResponseType> {
        return this.http
            .put(`${this.resourceUrl}/${questionId}/votes`, voteChange, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find all questions for id of exercise
     * @param {number} exerciseId
     * @return {Observable<EntityArrayResponseType>}
     */
    findQuestionsForExercise(exerciseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<StudentQuestion[]>(`api/exercises/${exerciseId}/student-questions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * find all questions for id of lecture
     * @param {number} lectureId
     * @return {Observable<EntityArrayResponseType>}
     */
    findQuestionsForLecture(lectureId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<StudentQuestion[]>(`api/lectures/${lectureId}/student-questions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * delete studentQuestion by id
     * @param {number} studentQuestionId
     * @return {Observable<HttpResponse<any>>}
     */
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
            creationDate: studentQuestion.creationDate && moment(studentQuestion.creationDate).isValid() ? moment(studentQuestion.creationDate).toJSON() : undefined,
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
