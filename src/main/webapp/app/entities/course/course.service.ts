import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { Course } from './course.model';
import { ProgrammingExercise } from '../programming-exercise/programming-exercise.model';
import { ModelingExercise } from '../modeling-exercise/modeling-exercise.model';
import { Participation } from '../participation/participation.model';
import { TextExercise } from '../text-exercise/text-exercise.model';
import { FileUploadExercise } from '../file-upload-exercise/file-upload-exercise.model';
import { Exercise } from '../exercise/exercise.model';
import { ExerciseService } from '../exercise/exercise.service';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

@Injectable({ providedIn: 'root' })
export class CourseService {
    private resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(course: Course): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(course);
        return this.http
            .post<Course>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(course: Course): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(course);
        return this.http
            .put<Course>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    findAll(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(`${this.resourceUrl}/for-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    // TODO: deprecated --> this method does not scale and should not be used in the future
    findAllParticipations(courseId: number): Observable<Participation[]> {
        return this.http.get<Participation[]>(`${this.resourceUrl}/${courseId}/participations`);
    }

    query(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(this.resourceUrl, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
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

    protected convertDateFromClient(course: Course): Course {
        const copy: Course = Object.assign({}, course, {
            startDate: course.startDate != null && moment(course.startDate).isValid() ? course.startDate.toJSON() : null,
            endDate: course.endDate != null && moment(course.endDate).isValid() ? course.endDate.toJSON() : null
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.startDate = res.body.startDate != null ? moment(res.body.startDate) : null;
            res.body.endDate = res.body.endDate != null ? moment(res.body.endDate) : null;
            res.body.exercises = this.exerciseService.convertExercisesDateFromServer(res.body.exercises);
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                course.startDate = course.startDate != null ? moment(course.startDate) : null;
                course.endDate = course.endDate != null ? moment(course.endDate) : null;
                course.exercises = this.exerciseService.convertExercisesDateFromServer(course.exercises);
            });
        }
        return res;
    }
}

@Injectable({ providedIn: 'root' })
export class CourseExerciseService {
    private resourceUrl = SERVER_API_URL + `api/courses`;

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

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

    /**
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     *
     * @param courseId
     */
    findAllProgrammingExercisesForCourse(courseId: number): Observable<HttpResponse<ProgrammingExercise[]>> {
        return this.http
            .get<ProgrammingExercise[]>(`${this.resourceUrl}/${courseId}/programming-exercises/`, { observe: 'response' })
            .map((res: HttpResponse<ProgrammingExercise[]>) => this.convertDateArrayFromServer(res));
    }

    findModelingExercise(courseId: number, exerciseId: number): Observable<ModelingExercise> {
        return this.http
            .get<ModelingExercise>(`${this.resourceUrl}/${courseId}/modeling-exercises/${exerciseId}`)
            .map((res: ModelingExercise) => this.convertDateFromServer(res));
    }

    /**
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     *
     * @param courseId
     */
    findAllModelingExercisesForCourse(courseId: number): Observable<HttpResponse<ModelingExercise[]>> {
        return this.http
            .get<ModelingExercise[]>(`${this.resourceUrl}/${courseId}/modeling-exercises/`, { observe: 'response' })
            .map((res: HttpResponse<ModelingExercise[]>) => this.convertDateArrayFromServer(res));
    }

    findTextExercise(courseId: number, exerciseId: number): Observable<TextExercise> {
        return this.http
            .get<TextExercise>(`${this.resourceUrl}/${courseId}/text-exercises/${exerciseId}`)
            .map((res: TextExercise) => this.convertDateFromServer(res));
    }

    /**
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     *
     * @param courseId
     */
    findAllTextExercisesForCourse(courseId: number): Observable<HttpResponse<TextExercise[]>> {
        return this.http
            .get<TextExercise[]>(`${this.resourceUrl}/${courseId}/text-exercises/`, { observe: 'response' })
            .map((res: HttpResponse<TextExercise[]>) => this.convertDateArrayFromServer(res));
    }

    findFileUploadExercise(courseId: number, exerciseId: number): Observable<FileUploadExercise> {
        return this.http
            .get<FileUploadExercise>(`${this.resourceUrl}/${courseId}/file-upload-exercises/${exerciseId}`)
            .map((res: FileUploadExercise) => this.convertDateFromServer(res));
    }

    /**
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     *
     * @param courseId
     */
    findAllFileUploadExercisesForCourse(courseId: number): Observable<HttpResponse<FileUploadExercise[]>> {
        return this.http
            .get<FileUploadExercise[]>(`${this.resourceUrl}/${courseId}/file-upload-exercises/`, { observe: 'response' })
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
        if (participation) {
            // convert date
            participation.initializationDate = participation.initializationDate ? moment(participation.initializationDate) : null;
            if (participation.exercise) {
                const exercise = participation.exercise;
                exercise.dueDate = exercise.dueDate ? moment(exercise.dueDate) : null;
                exercise.releaseDate = exercise.releaseDate ? moment(exercise.releaseDate) : null;
                exercise.participations = [participation];
                return participation;
            }
        }
        return participation;
    }

    protected convertDateFromServer<T extends Exercise>(res: T): T {
        res.releaseDate = res.releaseDate != null ? moment(res.releaseDate) : null;
        res.dueDate = res.dueDate != null ? moment(res.dueDate) : null;
        return res;
    }

    protected convertDateArrayFromServer<T extends Exercise>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        if (res.body) {
            res.body.forEach((exercise: T) => {
                exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
                exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
            });
        }
        return res;
    }
}
