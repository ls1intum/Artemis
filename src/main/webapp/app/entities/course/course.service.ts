import { Injectable, PipeTransform } from '@angular/core';
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

    query(req?: any): Observable<HttpResponse<Course[]>> {
        const options = createRequestOption(req);
        return this.http.get<Course[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Course[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
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
        copy.startDate = this.dateUtils
            .convertDateTimeFromServer(course.startDate);
        copy.endDate = this.dateUtils
            .convertDateTimeFromServer(course.endDate);
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

    constructor(private httpClient: HttpClient, private http: HttpClient, private dateUtils: JhiDateUtils) { }

    find(courseId: number, exerciseId: number): Observable<Exercise> {
        return this.http.get(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}`).map((res: HttpResponse<Exercise>) => {
            return this.convertExerciseFromServer(res.body);
        });
    }

    query(courseId: number, req?: any): Observable<HttpResponse<Course[]>> {
        const options = createRequestOption(req);
        return this.http.get(`${this.resourceUrl}/${courseId}/exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<Course[]>) => this.convertArrayResponse(res));
    }

    start(courseId: number, exerciseId: number) {
        return this.http.post(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/participations`, {}).map((res: any) => {
            if (res && res.exercise) {
                const exercise = this.convertGenericFromServer(res.exercise);
                exercise.participation = this.convertGenericFromServer(res);
                return exercise;
            }
            return this.convertGenericFromServer(res);
        });
    }

    private convertGenericFromServer(any) {
        const entity = Object.assign({}, any);
        return entity;
    }

    resume(courseId: number, exerciseId: number) {
        return this.http.put(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/resume-participation`, {}).map((res: any) => {
            if (res && res.exercise) {
                const exercise = this.convertGenericFromServer(res.exercise);
                exercise.participation = this.convertGenericFromServer(res);
                return exercise;
            }
            return this.convertGenericFromServer(res);
        });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Course = this.convertCourseFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Course[]>): HttpResponse<Course[]> {
        const jsonResponse: Course[] = res.body;
        const body: Course[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertCourseFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Exercise.
     */
    private convertExerciseFromServer(exercise: Exercise): Exercise {
        const entity: Exercise = Object.assign({}, exercise);
        entity.releaseDate = this.dateUtils
            .convertDateTimeFromServer(exercise.releaseDate);
        entity.dueDate = this.dateUtils
            .convertDateTimeFromServer(exercise.dueDate);
        return entity;
    }

    private convertParticipationFromServer(participation: Participation): Participation {
        const entity: Participation = Object.assign({}, participation);
        return entity;
    }

    private convertCourseFromServer(course: Course): Course {
        const entity: Course = Object.assign({}, course);
        return entity;
    }

    /**
     * Convert a Exercise to a JSON which can be sent to the server.
     */
    private convert(exercise: Exercise): Exercise {
        const copy: Exercise = Object.assign({}, exercise);

        copy.releaseDate = this.dateUtils.toDate(exercise.releaseDate);
        copy.dueDate = this.dateUtils.toDate(exercise.dueDate);
        return copy;
    }
}

// TODO: move into its own file

@Injectable()
export class CourseProgrammingExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    find(courseId: number, exerciseId: number): Observable<ProgrammingExercise> {
        return this.http.get(`${this.resourceUrl}/${courseId}/programming-exercises/${exerciseId}`).map((res: HttpResponse<ProgrammingExercise>) => {
            return this.convertItemFromServer(res.body);
        });
    }

    query(courseId: number, req?: any): Observable<HttpResponse<ProgrammingExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get(`${this.resourceUrl}/${courseId}/programming-exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<ProgrammingExercise[]>) => this.convertArrayResponse(res));
    }

    start(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http.post(`${this.resourceUrl}/${courseId}/programming-exercises/${exerciseId}/participations`, {}).map((res: HttpResponse<Participation>) => {
            if (res.body) {
                const exercise = res.body.exercise;
                exercise['participation'] = res.body;
                return exercise;
            }
            return this.convertItemFromServer(res.body);
        });
    }

    private convertResponse(res: HttpResponse<ProgrammingExercise>): HttpResponse<ProgrammingExercise> {
        const body: ProgrammingExercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<ProgrammingExercise[]>): HttpResponse<ProgrammingExercise[]> {
        const jsonResponse: ProgrammingExercise[] = res.body;
        const body: ProgrammingExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to ProgrammingExercise.
     */
    private convertItemFromServer(programmingExercise: ProgrammingExercise): ProgrammingExercise {
        const entity: ProgrammingExercise = Object.assign(new ProgrammingExercise(), programmingExercise);
        // TODO: convert date?
        return entity;
    }

    /**
     * Convert a ProgrammingExercise to a JSON which can be sent to the server.
     */
    private convert(programmingExercise: ProgrammingExercise): ProgrammingExercise {
        const copy: ProgrammingExercise = Object.assign({}, programmingExercise);
        return copy;
    }
}

@Injectable()
export class CourseParticipationService {
    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    findAll(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/participations`);
    }

    private convertParticipationFromServer(participation: Participation): Participation {
        const entity: Participation = Object.assign({}, participation);
        return entity;
    }

    private convertArrayResponse(res: HttpResponse<Participation[]>): HttpResponse<Participation[]> {
        const jsonResponse: Participation[] = res.body;
        const body: Participation[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertParticipationFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }
}

@Injectable()
export class CourseResultService {
    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    findAll(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/results`);
    }

    private convertResultFromServer(participation: Result): Result {
        const entity: Result = Object.assign({}, participation);
        return entity;
    }

    private convertArrayResponse(res: HttpResponse<Result[]>): HttpResponse<Result[]> {
        const jsonResponse: Result[] = res.body;
        const body: Result[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertResultFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }
}

@Injectable()
export class CourseScoresService {
    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    find(courseId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${courseId}/getAllCourseScoresOfCourseUsers`);
    }
}

@Injectable()
export class CourseScoreCalculationService {

    private SCORE_NORMALIZATION_VALUE = 0.01;
    private courses: Course[] = [];

    constructor(private dateUtils: JhiDateUtils, private http: HttpClient) { }

    calculateTotalScores(courseExercises: Exercise[]): Map<string, number> {
        const scores = new Map<string, number>();
        let absoluteScore = 0.0;
        let maxScore = 0;
        courseExercises.forEach( exercise => {
          if (exercise.maxScore !== null) {
              maxScore = maxScore + exercise.maxScore;
              const participation: Participation = this.getParticipationForExercise(exercise);
              if (participation !== undefined) {
                  const result: Result = this.getResultForParticipation(participation, exercise.dueDate);
                  absoluteScore = absoluteScore + result.score;
              }
          }
        });
        scores.set('absoluteScore', absoluteScore);
        if (maxScore > 0) {
            scores.set('relativeScore', round(((absoluteScore / maxScore) * 100)), 2);
        } else {
            scores.set('relativeScore', 0);
        }
        scores.set('maxScore', maxScore);
        return scores;
    }

    private round(value: number, exp: number) { // helper function to make actually rounding possible
      if (typeof exp === 'undefined' || +exp === 0) {
        return Math.round(value);
      }

      value = +value;
      exp = +exp;

      if (isNaN(value) || !(typeof exp === 'number' && exp % 1 === 0)) {
        return NaN;
      }

      // Shift
      value = value.toString().split('e');
      value = Math.round(+(value[0] + 'e' + (value[1] ? (+value[1] + exp) : exp)));

      // Shift back
      value = value.toString().split('e');
      return +(value[0] + 'e' + (value[1] ? (+value[1] - exp) : -exp));
    }

    setCourses(courses: Course[]) {
        const coursesArray: Course[] = courses;
        for (let i = 0; i < coursesArray.length; i++) {
            this.courses.push(this.convertCourseFromServer(coursesArray[i]));
        }
    }

    getCourses(): Course[] {
        return this.courses;
    }

    getCourse(courseId: number): Course {
        let course: Course;
        if(this.courses.length > 0) {
            for (let i = 0; i < this.courses.length; i++) {
                if (this.courses[i].id == courseId) {
                    course = this.courses[i];
                    return course;
                }
            }
        }
    }

    getExercisesByCourse(courseId: number) {
        const course: Course = this.getCourse(courseId);
        const exercises: Exercise[] = course.exercises;
        let exercisesArray: Exercise[] = [];
        for (let i = 0; i < exercises.length; i++) {
            exercisesArray.push(this.convertExerciseFromServer(exercises[i]));
        }
        return exercisesArray;
    }

    getParticipationForExercise(exercise: Exercise): Participation {
        let exerciseParticipation: Participation = exercise['participation'];
        return this.convertParticipationFromServer(exerciseParticipation);
    }

    getResultForParticipation(participation: Participation, dueDate: Date): Result {
        const results: Result[] = participation.results;
        const resultsArray: Result[] = [];
        let chosenResult: Result;

        if (results !== undefined) {
            for (let i = 0; i < results.length; i++) {
                resultsArray.push(this.convertResultFromServer(results[i]));
            }

            if (resultsArray.length <= 0) {
                chosenResult = new Result();
                chosenResult.score = 0;
                chosenResult.participation = participation;
                return chosenResult;
            }

            // sorting in descending order to have the last result at the beginning
            resultsArray.sort((result1, result2): number => {
                if (result1.completionDate > result2.completionDate) { return -1; }
                if (result1.completionDate < result2.completionDate) { return 1; }
                return 0;
            });

            if (dueDate != null && dueDate > resultsArray[0].completionDate) {
                // find the first result that is before the due date
                chosenResult = resultsArray[0];
            } else if (dueDate < resultsArray[0].completionDate) {
                chosenResult = new Result();
                chosenResult.score = 0;
            } else {
                chosenResult = resultsArray[resultsArray.length - 1];
            }

            // when the db has stored null for score
            if (chosenResult.score == null) {
                chosenResult.score = 0;
            }

            chosenResult.participation = participation;

        } else {
            chosenResult = new Result();
            chosenResult.score = 0;
        }

        return chosenResult;
    }

    private convertResultFromServer(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        copy.completionDate = this.dateUtils.toDate(result.completionDate);
        return copy;
    }

    private convertParticipationFromServer(participation: Participation): Participation {
        const entity: Participation = Object.assign({}, participation);
        return entity;
    }

    private convertExerciseFromServer(exercise: Exercise): Exercise {
        const entity: Exercise = Object.assign({}, exercise);
        entity.releaseDate = this.dateUtils
            .convertDateTimeFromServer(exercise.releaseDate);
        entity.dueDate = this.dateUtils
            .convertDateTimeFromServer(exercise.dueDate);
        return entity;
    }

    private convertCourseFromServer(course: Course): Course {
        const copy: Course = Object.assign({}, course);
        copy.startDate = this.dateUtils
            .convertDateTimeFromServer(course.startDate);
        copy.endDate = this.dateUtils
            .convertDateTimeFromServer(course.endDate);
        return copy;
    }
}

@Injectable()
export class CourseModelingExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    find(courseId: number, exerciseId: number): Observable<ModelingExercise> {
        return this.http.get(`${this.resourceUrl}/${courseId}/modeling-exercises/${exerciseId}`).map((res: HttpResponse<ModelingExercise>) => {
            return this.convertItemFromServer(res.body);
        });
    }

    query(courseId: number, req?: any): Observable<HttpResponse<ModelingExercise[]>> {
        const options = createRequestOption(req);
        return this.http.get(`${this.resourceUrl}/${courseId}/modeling-exercises/`, { params: options, observe: 'response' })
            .map((res: HttpResponse<ModelingExercise[]>) => this.convertArrayResponse(res));
    }

    start(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http.post(`${this.resourceUrl}/${courseId}/modeling-exercises/${exerciseId}/participations`, {}).map((res: HttpResponse<Participation>) => {
            if (res.body) {
                const exercise = res.body.exercise;
                exercise['participation'] = res.body;
                return exercise;
            }
            return this.convertItemFromServer(res.body);
        });
    }

    private convertArrayResponse(res: HttpResponse<ModelingExercise[]>): HttpResponse<ModelingExercise[]> {
        const jsonResponse: ModelingExercise[] = res.body;
        const body: ModelingExercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to ModelingExercise.
     */
    private convertItemFromServer(modelingExercise: ModelingExercise): ModelingExercise {
        const entity: ModelingExercise = Object.assign(new ModelingExercise(), modelingExercise);
        // TODO: convert date?
        return entity;
    }
}
