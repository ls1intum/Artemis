import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Observable, defer, finalize, map, tap } from 'rxjs';
import { NavigationSkipped, NavigationStart, Router } from '@angular/router';
import { participationChildRouteSegments } from 'app/course/overview/exercise-details/participation-child-route';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { convertDateFromServer } from 'app/foundation/util/date.utils';

@Injectable({ providedIn: 'root' })
export class CourseExerciseService {
    private http = inject(HttpClient);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private accountService = inject(AccountService);
    private router = inject(Router);

    /**
     * returns all programming exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId
     */
    findAllProgrammingExercisesForCourse(courseId: number): Observable<HttpResponse<ProgrammingExercise[]>> {
        return this.http
            .get<ProgrammingExercise[]>(`api/programming/courses/${courseId}/programming-exercises`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ProgrammingExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * returns all modeling exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId - the unique identifier of the course
     */
    findAllModelingExercisesForCourse(courseId: number): Observable<HttpResponse<ModelingExercise[]>> {
        return this.http
            .get<ModelingExercise[]>(`api/modeling/courses/${courseId}/modeling-exercises`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ModelingExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * returns all text exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId - the unique identifier of the course
     */
    findAllTextExercisesForCourse(courseId: number): Observable<HttpResponse<TextExercise[]>> {
        return this.http
            .get<TextExercise[]>(`api/text/courses/${courseId}/text-exercises`, { observe: 'response' })
            .pipe(map((res: HttpResponse<TextExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * returns all file upload exercises for the course corresponding to courseId
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * @param courseId - the unique identifier of the course
     */
    findAllFileUploadExercisesForCourse(courseId: number): Observable<HttpResponse<FileUploadExercise[]>> {
        return this.http
            .get<FileUploadExercise[]>(`api/fileupload/courses/${courseId}/file-upload-exercises`, { observe: 'response' })
            .pipe(map((res: HttpResponse<FileUploadExercise[]>) => this.processExercisesHttpResponses(res)));
    }

    /**
     * This method bundles recurring conversion steps for Course Exercise HttpResponses.
     * @param exercisesRes
     */
    private processExercisesHttpResponses(exercisesRes: HttpResponse<Exercise[]>): HttpResponse<Exercise[]> {
        this.convertExerciseResponseArrayDatesFromServer(exercisesRes);
        ExerciseService.convertExerciseCategoryArrayFromServer(exercisesRes);
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
        return this.http.post<StudentParticipation>(`api/exercise/exercises/${exerciseId}/participations`, {}).pipe(
            map((participation: StudentParticipation) => {
                return this.handleParticipation(participation);
            }),
        );
    }

    /**
     * starts the exercise with the identifier exerciseId
     * @param exerciseId - the unique identifier of the exercise
     * @param useGradedParticipation - flag indicating if the student wants to continue from their graded participation
     * @param exercise - the complete exercise already loaded by the caller
     */
    startPractice(exerciseId: number, useGradedParticipation: boolean, exercise: Exercise): Observable<StudentParticipation> {
        return defer(() => {
            const path = (url: string) => url.split(/[?#]/)[0];
            const originPath = path(this.router.url);
            const courseId = /^\/courses\/(\d+)\/exercises\//.exec(originPath)?.[1];
            const gradedParticipation = exercise.studentParticipations?.find((participation) => !participation.testRun);
            const gradedSegments = gradedParticipation ? participationChildRouteSegments(exercise, gradedParticipation) : undefined;
            const exercisePath = `/courses/${courseId}/exercises`;
            const gradedEditorPath = gradedSegments ? `${exercisePath}/${gradedSegments.join('/')}` : undefined;
            // Only bridge the details-to-editor redirect that can destroy the initiating button. Starts from a course
            // overview, an existing editor, or an unrelated page keep their caller's navigation behaviour.
            let followRedirect = !!gradedSegments && (originPath === `${exercisePath}/${exerciseId}` || originPath === `${exercisePath}/${gradedSegments.slice(0, 2).join('/')}`);
            const pendingNavigation = this.router.currentNavigation();
            const pendingPath = pendingNavigation ? path((pendingNavigation.finalUrl ?? pendingNavigation.extractedUrl).toString()) : undefined;
            if (pendingPath && pendingPath !== gradedEditorPath) {
                followRedirect = false;
            }
            let editorRedirectStarted = followRedirect && pendingPath === gradedEditorPath;
            const navigationSubscription = followRedirect
                ? this.router.events.subscribe((event) => {
                      if (event instanceof NavigationStart) {
                          if (!editorRedirectStarted && path(event.url) === gradedEditorPath) {
                              editorRedirectStarted = true;
                          } else {
                              // A later choice (including leaving and coming back) wins over the pending practice start.
                              followRedirect = false;
                          }
                      } else if (event instanceof NavigationSkipped) {
                          followRedirect = false;
                      }
                  })
                : undefined;
            return this.http.post<StudentParticipation>(`api/exercise/exercises/${exerciseId}/participations/practice?useGradedParticipation=${useGradedParticipation}`, {}).pipe(
                map((participation: StudentParticipation) => this.handleParticipation(participation)),
                tap((participation) => {
                    if (!followRedirect || !editorRedirectStarted || !participation.testRun || path(this.router.url) !== gradedEditorPath) {
                        return;
                    }
                    const practiceSegments = participationChildRouteSegments(exercise, participation);
                    if (practiceSegments) {
                        void this.router.navigate(['/courses', courseId, 'exercises', ...practiceSegments], { replaceUrl: true });
                    }
                }),
                finalize(() => navigationSubscription?.unsubscribe()),
            );
        });
    }

    /**
     * resumes the programming exercise with the identifier exerciseId
     * @param exerciseId - the unique identifier of the exercise
     * @param participationId - the unique identifier of the participation to continue
     */
    resumeProgrammingExercise(exerciseId: number, participationId: number): Observable<StudentParticipation> {
        return this.http.put<StudentParticipation>(`api/exercise/exercises/${exerciseId}/participations/${participationId}/resume-programming-participation`, {}).pipe(
            map((participation: StudentParticipation) => {
                return this.handleParticipation(participation);
            }),
        );
    }

    requestFeedback(exerciseId: number, participationId: number): Observable<StudentParticipation> {
        return this.http
            .put<StudentParticipation>(`api/exercise/exercises/${exerciseId}/participations/${participationId}/request-feedback`, {})
            .pipe(map((participation: StudentParticipation) => participation));
    }

    /**
     * handle the given student participation by adding in the participationWebsocketService
     * @param participation - the participation to be handled
     */
    handleParticipation(participation: StudentParticipation): StudentParticipation {
        if (participation) {
            // convert date
            participation.initializationDate = convertDateFromServer(participation.initializationDate);
            if (participation.exercise) {
                const exercise = participation.exercise;
                this.convertExerciseDatesFromServer(exercise);
                exercise.studentParticipations = [participation];
            }
            this.participationWebsocketService.addParticipation(participation);
        }
        return participation;
    }

    convertExerciseDatesFromServer<T extends Exercise>(res: T): T {
        res.releaseDate = convertDateFromServer(res.releaseDate);
        res.startDate = convertDateFromServer(res.startDate);
        res.dueDate = convertDateFromServer(res.dueDate);
        res.assessmentDueDate = convertDateFromServer(res.assessmentDueDate);
        res.exampleSolutionPublicationDate = convertDateFromServer(res.exampleSolutionPublicationDate);
        return res;
    }

    protected convertExerciseResponseArrayDatesFromServer<T extends Exercise>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        if (res.body) {
            res.body.forEach((exercise: T) => {
                exercise.releaseDate = convertDateFromServer(exercise.releaseDate);
                exercise.startDate = convertDateFromServer(exercise.startDate);
                exercise.dueDate = convertDateFromServer(exercise.dueDate);
                exercise.assessmentDueDate = convertDateFromServer(exercise.assessmentDueDate);
                exercise.exampleSolutionPublicationDate = convertDateFromServer(exercise.exampleSolutionPublicationDate);
            });
        }
        return res;
    }

    /**
     * returns all exercises for the course corresponding to courseId
     * @param courseId - the unique identifier of the course
     */
    findAllExercisesWithDueDatesForCourse(courseId: number): Observable<HttpResponse<Exercise[]>> {
        return this.http
            .get<Exercise[]>(`api/course/courses/${courseId}/all-exercises-with-due-dates`, { observe: 'response' })
            .pipe(map((res: HttpResponse<Exercise[]>) => this.processExercisesHttpResponses(res)));
    }
}
