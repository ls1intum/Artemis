import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { map, Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import dayjs from 'dayjs';

@Injectable({ providedIn: 'root' })
export class CourseExerciseService {
    private resourceUrl = SERVER_API_URL + `api/courses`;

    constructor(
        private http: HttpClient,
        private participationWebsocketService: ParticipationWebsocketService,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
    ) {}

    /**
     * returns all programming exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId
     */
    findAllProgrammingExercisesForCourse(courseId: number): Observable<HttpResponse<ProgrammingExercise[]>> {
        return this.http
            .get<ProgrammingExercise[]>(`${this.resourceUrl}/${courseId}/programming-exercises/`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ProgrammingExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * returns all modeling exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId - the unique identifier of the course
     */
    findAllModelingExercisesForCourse(courseId: number): Observable<HttpResponse<ModelingExercise[]>> {
        return this.http
            .get<ModelingExercise[]>(`${this.resourceUrl}/${courseId}/modeling-exercises/`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ModelingExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * returns all text exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId - the unique identifier of the course
     */
    findAllTextExercisesForCourse(courseId: number): Observable<HttpResponse<TextExercise[]>> {
        return this.http
            .get<TextExercise[]>(`${this.resourceUrl}/${courseId}/text-exercises/`, { observe: 'response' })
            .pipe(map((res: HttpResponse<TextExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * returns all file upload exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId - the unique identifier of the course
     */
    findAllFileUploadExercisesForCourse(courseId: number): Observable<HttpResponse<FileUploadExercise[]>> {
        return this.http
            .get<FileUploadExercise[]>(`${this.resourceUrl}/${courseId}/file-upload-exercises/`, { observe: 'response' })
            .pipe(map((res: HttpResponse<FileUploadExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * This method bundles recurring conversion steps for Course Exercise HttpResponses.
     * @param exercisesRes
     * @private
     */
    private processExercisesHttpResponses(exercisesRes: HttpResponse<Exercise[]>): HttpResponse<Exercise[]> {
        this.convertDateArrayFromServer(exercisesRes);
        this.exerciseService.convertExerciseCategoryArrayFromServer(exercisesRes);
        if (exercisesRes.body) {
            exercisesRes.body.forEach((exercise) => this.accountService.setAccessRightsForExercise(exercise));
        }
        return exercisesRes;
    }

    /**
     * starts the exercise with the identifier exerciseId
     * @param exerciseId - the unique identifier of the exercise
     */
    startExercise(exerciseId: number): Observable<StudentParticipation> {
        return this.http.post<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/participations`, {}).pipe(
            map((participation: StudentParticipation) => {
                return this.handleParticipation(participation);
            }),
        );
    }

    /**
     * resumes the programming exercise with the identifier exerciseId
     * @param exerciseId - the unique identifier of the exercise
     */
    resumeProgrammingExercise(exerciseId: number): Observable<StudentParticipation> {
        return this.http.put<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/resume-programming-participation`, {}).pipe(
            map((participation: StudentParticipation) => {
                return this.handleParticipation(participation);
            }),
        );
    }

    /**
     * handle the given student participation by adding in the participationWebsocketService
     * @param participation - the participation to be handled
     */
    handleParticipation(participation: StudentParticipation) {
        if (participation) {
            // convert date
            participation.initializationDate = participation.initializationDate ? dayjs(participation.initializationDate) : undefined;
            if (participation.exercise) {
                const exercise = participation.exercise;
                exercise.dueDate = exercise.dueDate ? dayjs(exercise.dueDate) : undefined;
                exercise.releaseDate = exercise.releaseDate ? dayjs(exercise.releaseDate) : undefined;
                exercise.studentParticipations = [participation];
            }
            this.participationWebsocketService.addParticipation(participation);
        }
        return participation;
    }

    convertDateFromServer<T extends Exercise>(res: T): T {
        res.releaseDate = res.releaseDate ? dayjs(res.releaseDate) : undefined;
        res.dueDate = res.dueDate ? dayjs(res.dueDate) : undefined;
        return res;
    }

    protected convertDateArrayFromServer<T extends Exercise>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        if (res.body) {
            res.body.forEach((exercise: T) => {
                exercise.releaseDate = exercise.releaseDate ? dayjs(exercise.releaseDate) : undefined;
                exercise.dueDate = exercise.dueDate ? dayjs(exercise.dueDate) : undefined;
                exercise.assessmentDueDate = exercise.assessmentDueDate ? dayjs(exercise.assessmentDueDate) : undefined;
            });
        }
        return res;
    }
}
