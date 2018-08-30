import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { JhiDateUtils } from 'ng-jhipster';

import { Course } from './course.model';
import { createRequestOption } from '../../shared';
import { Exercise } from '../exercise/exercise.model';
import { ProgrammingExercise } from '../programming-exercise/programming-exercise.model';
import { ModelingExercise } from '../modeling-exercise/modeling-exercise.model';
import { Participation } from '../participation';
import { Result } from '../result/result.model';
import { TextExercise } from 'app/entities/text-exercise';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';

export type EntityResponseType = HttpResponse<Course>;

@Injectable()
export class CourseService {

    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(course: Course): Observable<EntityResponseType> {
        const copy = this.convert(course);
        return this.http.post<Course>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(course: Course): Observable<EntityResponseType> {
        const copy = this.convert(course);
        return this.http.put<Course>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Course>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    findAll(): Observable<Course[]> {
        return this.http.get<Course[]>(`${this.resourceUrl}/for-dashboard`);
    }

    // TODO: deprecated --> this method does not scale and should not be used in the future
    findAllParticipations(courseId: number): Observable<Participation[]> {
        return this.http.get<Participation[]>(`${this.resourceUrl}/${courseId}/participations`);
    }

    query(req?: any): Observable<HttpResponse<Course[]>> {
        const options = createRequestOption(req);
        return this.http.get<Course[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Course[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    getAllCourseScoresOfCourseUsers(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/getAllCourseScoresOfCourseUsers`);
    }

    findAllResults(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/results`);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Course = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Course[]>): HttpResponse<Course[]> {
        const jsonResponse: Course[] = res.body;
        const body: Course[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Course.
     */
    private convertItemFromServer(course: Course): Course {
        const copy: Course = Object.assign({}, course);
        copy.startDate = this.dateUtils.convertDateTimeFromServer(course.startDate);
        copy.endDate = this.dateUtils.convertDateTimeFromServer(course.endDate);
        return copy;
    }

    /**
     * Convert a Course to a JSON which can be sent to the server.
     */
    private convert(course: Course): Course {
        const copy: Course = Object.assign({}, course);
        copy.startDate = this.dateUtils.toDate(course.startDate);
        copy.endDate = this.dateUtils.toDate(course.endDate);
        return copy;
    }
}

@Injectable()
export class CourseExerciseService {
    private resourceUrl =  SERVER_API_URL + `api/courses`;

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    findExercise(courseId: number, exerciseId: number): Observable<Exercise> {
        return this.http.get(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}`).map((res: HttpResponse<Exercise>) => {
            return this.convertExerciseFromServer(res.body);
        });
    }

    findAllExercises(courseId: number, req?: any): Observable<HttpResponse<Exercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<Exercise[]>(`${this.resourceUrl}/${courseId}/exercises/`, { params: options, observe: 'response' });
    }

    // exercise specific calls

    findProgrammingExercise(courseId: number, exerciseId: number): Observable<ProgrammingExercise> {
        return this.http.get<ProgrammingExercise>(`${this.resourceUrl}/${courseId}/programming-exercises/${exerciseId}`);
    }

    findAllProgrammingExercises(courseId: number, req?: any): Observable<HttpResponse<ProgrammingExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<ProgrammingExercise[]>(`${this.resourceUrl}/${courseId}/programming-exercises/`, { params: options, observe: 'response' });
    }

    findModelingExercise(courseId: number, exerciseId: number): Observable<ModelingExercise> {
        return this.http.get<ModelingExercise>(`${this.resourceUrl}/${courseId}/modeling-exercises/${exerciseId}`);
    }

    findAllModelingExercises(courseId: number, req?: any): Observable<HttpResponse<ModelingExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<ModelingExercise[]>(`${this.resourceUrl}/${courseId}/modeling-exercises/`, { params: options, observe: 'response' });
    }

    findTextExercise(courseId: number, exerciseId: number): Observable<TextExercise> {
        return this.http.get<TextExercise>(`${this.resourceUrl}/${courseId}/text-exercises/${exerciseId}`);
    }

    findAllTextExercises(courseId: number, req?: any): Observable<HttpResponse<TextExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<TextExercise[]>(`${this.resourceUrl}/${courseId}/text-exercises/`, { params: options, observe: 'response' });
    }

    findFileUploadExercise(courseId: number, exerciseId: number): Observable<FileUploadExercise> {
        return this.http.get<FileUploadExercise>(`${this.resourceUrl}/${courseId}/file-upload-exercises/${exerciseId}`);
    }

    findAllFileUploadExercises(courseId: number, req?: any): Observable<HttpResponse<FileUploadExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<FileUploadExercise[]>(`${this.resourceUrl}/${courseId}/file-upload-exercises/`, { params: options, observe: 'response' });
    }

    startExercise(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http.post<Participation>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/participations`, {}).map((participation: Participation) => {
            return this.handleParticipation(participation);
        });
    }

    resumeExercise(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http.put(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/resume-participation`, {}).map((participation: Participation) => {
            return this.handleParticipation(participation);
        });
    }

    handleParticipation(participation: Participation) {
        if (participation && participation.exercise) {
            const exercise = participation.exercise;
            exercise.participations = [participation];
            return participation;
        }
        return participation;
    }

    /**
     * Convert a returned JSON object to Exercise, i.e. make sure that the dates are parsed correctly
     */
    private convertExerciseFromServer(exercise: Exercise): Exercise {
        const entity: Exercise = Object.assign({}, exercise);
        entity.releaseDate = this.dateUtils.convertDateTimeFromServer(exercise.releaseDate);
        entity.dueDate = this.dateUtils.convertDateTimeFromServer(exercise.dueDate);
        return entity;
    }
}
