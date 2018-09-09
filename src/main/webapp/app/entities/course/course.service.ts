import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { Course } from './course.model';
import { createRequestOption } from '../../shared';
import { Exercise } from '../exercise/exercise.model';
import { ProgrammingExercise } from '../programming-exercise/programming-exercise.model';
import { ModelingExercise } from '../modeling-exercise/modeling-exercise.model';
import { Participation } from '../participation';
import { TextExercise } from 'app/entities/text-exercise';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

@Injectable()
export class CourseService {
    private resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) {}

    create(course: Course): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(course);
        return this.http
            .post<Course>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(course: Course): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(course);
        return this.http
            .put<Course>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findAll(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(`${this.resourceUrl}/for-dashboard`, { observe: 'response' })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
        //TODO: do we need to convert the date for exercise included in this call as well?
    }

    // TODO: deprecated --> this method does not scale and should not be used in the future
    findAllParticipations(courseId: number): Observable<Participation[]> {
        return this.http.get<Participation[]>(`${this.resourceUrl}/${courseId}/participations`);
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Course[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    getAllCourseScoresOfCourseUsers(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/getAllCourseScoresOfCourseUsers`);
    }

    findAllResults(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/results`);
    }

    private convertDateFromClient(course: Course): Course {
        const copy: Course = Object.assign({}, course, {
            releaseDate: course.startDate != null && course.startDate.isValid() ? course.startDate.toJSON() : null,
            dueDate: course.endDate != null && course.endDate.isValid() ? course.endDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.startDate = res.body.startDate != null ? moment(res.body.startDate) : null;
        res.body.endDate = res.body.endDate != null ? moment(res.body.endDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((course: Course) => {
            course.startDate = course.startDate != null ? moment(course.startDate) : null;
            course.endDate = course.endDate != null ? moment(course.endDate) : null;
        });
        return res;
    }
}

@Injectable()
export class CourseExerciseService {
    private resourceUrl = SERVER_API_URL + `api/courses`;

    constructor(private http: HttpClient) {}

    findExercise(courseId: number, exerciseId: number): Observable<Exercise> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}`)
            .map((res: Exercise) => this.convertDateFromServer(res));
    }

    findAllExercises(courseId: number, req?: any): Observable<HttpResponse<Exercise[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<Exercise[]>(`${this.resourceUrl}/${courseId}/exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<Exercise[]>) => this.convertDateArrayFromServer(res));
    }

    // exercise specific calls

    findProgrammingExercise(courseId: number, exerciseId: number): Observable<ProgrammingExercise> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${courseId}/programming-exercises/${exerciseId}`)
            .map((res: ProgrammingExercise) => this.convertDateFromServer(res));
    }

    findAllProgrammingExercises(courseId: number, req?: any): Observable<HttpResponse<ProgrammingExercise[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<ProgrammingExercise[]>(`${this.resourceUrl}/${courseId}/programming-exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<ProgrammingExercise[]>) => this.convertDateArrayFromServer(res));
    }

    findModelingExercise(courseId: number, exerciseId: number): Observable<ModelingExercise> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${courseId}/modeling-exercises/${exerciseId}`)
            .map((res: ModelingExercise) => this.convertDateFromServer(res));
    }

    findAllModelingExercises(courseId: number, req?: any): Observable<HttpResponse<ModelingExercise[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<ModelingExercise[]>(`${this.resourceUrl}/${courseId}/modeling-exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<ModelingExercise[]>) => this.convertDateArrayFromServer(res));
    }

    findTextExercise(courseId: number, exerciseId: number): Observable<TextExercise> {
        return this.http
            .get<TextExercise>(`${this.resourceUrl}/${courseId}/text-exercises/${exerciseId}`)
            .map((res: TextExercise) => this.convertDateFromServer(res));
    }

    findAllTextExercises(courseId: number, req?: any): Observable<HttpResponse<TextExercise[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<TextExercise[]>(`${this.resourceUrl}/${courseId}/text-exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<TextExercise[]>) => this.convertDateArrayFromServer(res));
    }

    findFileUploadExercise(courseId: number, exerciseId: number): Observable<FileUploadExercise> {
        return this.http
            .get<FileUploadExercise>(`${this.resourceUrl}/${courseId}/file-upload-exercises/${exerciseId}`)
            .map((res: FileUploadExercise) => this.convertDateFromServer(res));
    }

    findAllFileUploadExercises(courseId: number, req?: any): Observable<HttpResponse<FileUploadExercise[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<FileUploadExercise[]>(`${this.resourceUrl}/${courseId}/file-upload-exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<FileUploadExercise[]>) => this.convertDateArrayFromServer(res));
    }

    startExercise(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http
            .post<Participation>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/participations`, {})
            .map((participation: Participation) => {
                return this.handleParticipation(participation);
            });
    }

    resumeExercise(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http
            .put(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/resume-participation`, {})
            .map((participation: Participation) => {
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

    private convertDateFromServer<T extends Exercise>(res: T): T {
        res.releaseDate = res.releaseDate != null ? moment(res.releaseDate) : null;
        res.dueDate = res.dueDate != null ? moment(res.dueDate) : null;
        return res;
    }

    private convertDateArrayFromServer<T extends Exercise>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        res.body.forEach((exercise: T) => {
            exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
            exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        });
        return res;
    }
}
