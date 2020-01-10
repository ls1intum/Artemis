import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { Course } from './course.model';
import { ProgrammingExercise } from '../programming-exercise/programming-exercise.model';
import { ModelingExercise } from '../modeling-exercise/modeling-exercise.model';
import { TextExercise } from '../text-exercise/text-exercise.model';
import { FileUploadExercise } from '../file-upload-exercise/file-upload-exercise.model';
import { Exercise } from '../exercise/exercise.model';
import { ExerciseService } from '../exercise/exercise.service';
import { User } from 'app/core';
import { NotificationService } from 'app/entities/notification';
import { LectureService } from 'app/entities/lecture/lecture.service';
import { StatsForDashboard } from 'app/instructor-course-dashboard/stats-for-dashboard.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

@Injectable({ providedIn: 'root' })
export class CourseService {
    private resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(
        private http: HttpClient,
        private exerciseService: ExerciseService,
        private lectureService: LectureService,
        private notificationService: NotificationService,
        private accountService: AccountService,
    ) {}

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

    find(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
            .pipe(map((res: EntityResponseType) => this.checkAccessRightsCourse(res)));
    }

    findWithExercises(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/with-exercises`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    findWithExercisesAndParticipations(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/with-exercises-and-relevant-participations`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    findAll(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(`${this.resourceUrl}/for-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)))
            .pipe(map((res: EntityArrayResponseType) => this.checkAccessRights(res)))
            .pipe(map((res: EntityArrayResponseType) => this.reconnectObjects(res)))
            .pipe(map((res: EntityArrayResponseType) => this.subscribeToCourseNotifications(res)));
    }

    findAllParticipationsWithResults(courseId: number): Observable<StudentParticipation[]> {
        return this.http.get<StudentParticipation[]>(`${this.resourceUrl}/${courseId}/participations`);
    }

    findAllResultsOfCourseForExerciseAndCurrentUser(courseId: number): Observable<Course> {
        return this.http.get<Course>(`${this.resourceUrl}/${courseId}/results`);
    }

    findAllToRegister(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(`${this.resourceUrl}/to-register`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    getForTutors(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/for-tutor-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    getStatsForTutors(courseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${courseId}/stats-for-tutor-dashboard`, { observe: 'response' });
    }

    // the body is null, because the server can identify the user anyway
    registerForCourse(courseId: number): Observable<HttpResponse<User>> {
        return this.http
            .post<User>(`${this.resourceUrl}/${courseId}/register`, null, { observe: 'response' })
            .pipe(
                map((res: HttpResponse<User>) => {
                    if (res.body != null) {
                        this.accountService.syncGroups(res.body);
                    }
                    return res;
                }),
            );
    }

    query(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(this.resourceUrl, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)))
            .pipe(map((res: EntityArrayResponseType) => this.checkAccessRights(res)))
            .pipe(map((res: EntityArrayResponseType) => this.subscribeToCourseNotifications(res)));
    }

    delete(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}`, { observe: 'response' });
    }

    getStatsForInstructors(courseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${courseId}/stats-for-instructor-dashboard`, { observe: 'response' });
    }

    findAllCategoriesOfCourse(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(`${this.resourceUrl}/${courseId}/categories`, { observe: 'response' });
    }

    private convertDateFromClient(course: Course): Course {
        const copy: Course = Object.assign({}, course, {
            startDate: course.startDate != null && moment(course.startDate).isValid() ? course.startDate.toJSON() : null,
            endDate: course.endDate != null && moment(course.endDate).isValid() ? course.endDate.toJSON() : null,
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.startDate = res.body.startDate != null ? moment(res.body.startDate) : null;
            res.body.endDate = res.body.endDate != null ? moment(res.body.endDate) : null;
            res.body.exercises = this.exerciseService.convertExercisesDateFromServer(res.body.exercises);
            res.body.lectures = this.lectureService.convertDatesForLecturesFromServer(res.body.lectures);
        }
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                course.startDate = course.startDate != null ? moment(course.startDate) : null;
                course.endDate = course.endDate != null ? moment(course.endDate) : null;
                course.exercises = this.exerciseService.convertExercisesDateFromServer(course.exercises);
                course.lectures = this.lectureService.convertDatesForLecturesFromServer(course.lectures);
            });
        }
        return res;
    }

    private subscribeToCourseNotifications(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            this.notificationService.handleCoursesNotifications(res.body);
        }
        return res;
    }

    private checkAccessRightsCourse(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(res.body);
            res.body.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(res.body);
        }
        return res;
    }

    private reconnectObjects(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                // TODO: implement
            });
        }
        return res;
    }

    private checkAccessRights(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                course.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(course);
                course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(course);
            });
        }
        return res;
    }
}

@Injectable({ providedIn: 'root' })
export class CourseExerciseService {
    private resourceUrl = SERVER_API_URL + `api/courses`;

    constructor(private http: HttpClient, private participationWebsocketService: ParticipationWebsocketService) {}

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
        return this.http.get<ModelingExercise>(`${this.resourceUrl}/${courseId}/modeling-exercises/${exerciseId}`).map((res: ModelingExercise) => this.convertDateFromServer(res));
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
        return this.http.get<TextExercise>(`${this.resourceUrl}/${courseId}/text-exercises/${exerciseId}`).map((res: TextExercise) => this.convertDateFromServer(res));
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

    startExercise(courseId: number, exerciseId: number): Observable<StudentParticipation> {
        return this.http.post<StudentParticipation>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/participations`, {}).map((participation: StudentParticipation) => {
            return this.handleParticipation(participation);
        });
    }

    resumeProgrammingExercise(courseId: number, exerciseId: number): Observable<StudentParticipation> {
        return this.http
            .put<StudentParticipation>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/resume-programming-participation`, {})
            .map((participation: StudentParticipation) => {
                return this.handleParticipation(participation);
            });
    }

    handleParticipation(participation: StudentParticipation) {
        if (participation) {
            // convert date
            participation.initializationDate = participation.initializationDate ? moment(participation.initializationDate) : null;
            if (participation.exercise) {
                const exercise = participation.exercise;
                exercise.dueDate = exercise.dueDate ? moment(exercise.dueDate) : null;
                exercise.releaseDate = exercise.releaseDate ? moment(exercise.releaseDate) : null;
                exercise.studentParticipations = [participation];
            }
            this.participationWebsocketService.addParticipation(participation);
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
