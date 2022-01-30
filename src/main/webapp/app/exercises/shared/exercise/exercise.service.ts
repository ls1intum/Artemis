import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, IncludedInOverallScore, ParticipationStatus } from 'app/entities/exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ParticipationService } from '../participation/participation.service';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { LtiConfiguration } from 'app/entities/lti-configuration.model';
import { CourseExerciseStatisticsDTO } from 'app/exercises/shared/exercise/exercise-statistics-dto.model';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { User } from 'app/core/user/user.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';

export type EntityResponseType = HttpResponse<Exercise>;
export type EntityArrayResponseType = HttpResponse<Exercise[]>;

export interface ExerciseServicable<T extends Exercise> {
    create(exercise: T): Observable<HttpResponse<T>>;
    import?(exercise: T): Observable<HttpResponse<T>>;
    update(exercise: T, req?: any): Observable<HttpResponse<T>>;
    reevaluateAndUpdate(exercise: T, req?: any): Observable<HttpResponse<T>>;
}

@Injectable({ providedIn: 'root' })
export class ExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient, private participationService: ParticipationService, private accountService: AccountService, private translateService: TranslateService) {}

    /**
     * Persist a new exercise
     * @param { Exercise } exercise - Exercise that will be persisted
     * return
     */
    create(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        copy.categories = this.stringifyExerciseCategories(copy);
        return this.http.post<Exercise>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processExerciseEntityResponse(res)));
    }

    /**
     * Update existing exercise
     * @param { Exercise } exercise - Exercise that will be updated
     */
    update(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        copy.categories = this.stringifyExerciseCategories(copy);
        return this.http.put<Exercise>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processExerciseEntityResponse(res)));
    }

    /**
     * Validates if the date is correct
     */
    validateDate(exercise: Exercise) {
        exercise.dueDateError = exercise.releaseDate && exercise.dueDate ? !exercise.dueDate.isAfter(exercise.releaseDate) : false;

        if (exercise.releaseDate && exercise.assessmentDueDate) {
            if (exercise.dueDate) {
                exercise.assessmentDueDateError = exercise.assessmentDueDate.isBefore(exercise.dueDate) || exercise.assessmentDueDate.isBefore(exercise.releaseDate);
                return;
            } else {
                exercise.assessmentDueDateError = true;
                return;
            }
        }

        if (exercise.assessmentDueDate) {
            if (exercise.dueDate) {
                exercise.assessmentDueDateError = !exercise.assessmentDueDate.isAfter(exercise.dueDate);
            } else {
                exercise.assessmentDueDateError = true;
            }
        }
    }

    /**
     * Get exercise with exerciseId from server
     * @param { number } exerciseId - Exercise that should be loaded
     */
    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExerciseEntityResponse(res)));
    }

    /**
     * Fetches the title of the exercise with the given id
     *
     * @param exerciseId the id of the exercise
     * @return the title of the exercise in an HttpResponse, or an HttpErrorResponse on error
     */
    getTitle(exerciseId: number): Observable<HttpResponse<string>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/title`, { observe: 'response', responseType: 'text' });
    }

    /**
     * Get exercise details including all results for the currently logged in user
     * @param { number } exerciseId - Id of the exercise to get the repos from
     */
    getExerciseDetails(exerciseId: number): Observable<EntityResponseType> {
        return this.http.get<Exercise>(`${this.resourceUrl}/${exerciseId}/details`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.processExerciseEntityResponse(res);

                if (res.body) {
                    // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
                    if (res.body.exerciseHints === undefined) {
                        res.body.exerciseHints = [];
                    }
                    if (res.body.posts === undefined) {
                        res.body.posts = [];
                    }
                }
                return res;
            }),
        );
    }

    /**
     * Delete student build plans (except BASE/SOLUTION) and optionally git repositories of all exercise student participations.
     * @param { number } exerciseId - programming exercise for which build plans in respective student participations are deleted
     * @param { boolean } deleteRepositories - if true, the repositories get deleted
     */
    cleanup(exerciseId: number, deleteRepositories: boolean): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('deleteRepositories', deleteRepositories.toString());
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/cleanup`, { params, observe: 'response' });
    }

    /**
     * Resets an Exercise with exerciseId by deleting all its participations.
     * @param { number } exerciseId - Id of exercise that should be resetted
     */
    reset(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/reset`, { observe: 'response' });
    }

    getUpcomingExercises(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exercise[]>(`${this.resourceUrl}/upcoming`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Returns all exercises of the given exercises parameter which have no due date or a due date in the future within delayInDays days
     * The returned exercises are sorted by due date, with the earliest due date being sorted first, and no due date sorted last
     *
     * @param { Exercise[] } exercises - The exercises to filter and sort
     * @param { number } delayInDays - The amount of days an exercise can be due into the future, defaults to seven
     */
    getNextExercisesForDays(exercises: Exercise[], delayInDays = 7): Exercise[] {
        return exercises
            .filter((exercise) => {
                if (!exercise.dueDate) {
                    return true;
                }

                const dueDate = exercise.dueDate!;
                return dayjs().isBefore(dueDate) && dayjs().add(delayInDays, 'day').isSameOrAfter(dueDate);
            })
            .sort((exerciseA: Exercise, exerciseB: Exercise) => {
                if (!exerciseA.dueDate) {
                    // If A has no due date, sort B first
                    return 1;
                } else if (!exerciseB.dueDate) {
                    // If B has no due date, sort A first
                    return -1;
                } else {
                    // Sort the one with the next due date first
                    return exerciseA.dueDate.isBefore(exerciseB.dueDate) ? -1 : 1;
                }
            });
    }

    /**
     * Returns an active quiz, a visible quiz or an exercise due in delayInHours or 12 hours if not specified
     * @param exercises - Considered exercises
     * @param delayInHours - If set, amount of hours that are considered
     * @param student - Needed when individual due dates for course exercises should be considered
     */
    getNextExerciseForHours(exercises?: Exercise[], delayInHours = 12, student?: User) {
        // check for quiz exercise in order to prioritize before other exercise types
        const nextQuizExercises = exercises?.filter((exercise: QuizExercise) => exercise.type === ExerciseType.QUIZ && !exercise.ended);
        return (
            // 1st priority is an active quiz
            nextQuizExercises?.find((exercise: QuizExercise) => this.isActiveQuiz(exercise)) ||
            // 2nd priority is a visible quiz
            nextQuizExercises?.find((exercise: QuizExercise) => exercise.isVisibleBeforeStart) ||
            // 3rd priority is the next due exercise
            exercises?.find((exercise) => {
                const studentParticipation = student ? exercise.studentParticipations?.find((participation) => participation.student?.id === student?.id) : undefined;
                const dueDate = getExerciseDueDate(exercise, studentParticipation);
                return dayjs().isBefore(dueDate) && dayjs().add(delayInHours, 'hours').isSameOrAfter(dueDate);
            })
        );
    }

    isActiveQuiz(exercise: Exercise) {
        return (
            exercise.participationStatus === ParticipationStatus.QUIZ_UNINITIALIZED ||
            exercise.participationStatus === ParticipationStatus.QUIZ_ACTIVE ||
            exercise.participationStatus === ParticipationStatus.QUIZ_SUBMITTED
        );
    }

    /**
     * Converts all dates of a server-exercise to the client timezone
     * @param { Exercise } exercise - Exercise from server whose date is adjusted
     * @returns { Exercise } - Exercise with adjusted times
     */
    convertExerciseDateFromServer(exercise?: Exercise) {
        if (exercise) {
            exercise.releaseDate = exercise.releaseDate ? dayjs(exercise.releaseDate) : undefined;
            exercise.dueDate = exercise.dueDate ? dayjs(exercise.dueDate) : undefined;
            exercise.assessmentDueDate = exercise.assessmentDueDate ? dayjs(exercise.assessmentDueDate) : undefined;
            exercise.studentParticipations = this.participationService.convertParticipationsDateFromServer(exercise.studentParticipations);
        }
        return exercise;
    }

    /**
     * Converts all dates of server-exercises to the client timezone
     * @param { Exercise[] } exercises - Array of server-exercises whose date are adjusted
     * @returns { Exercise[] } - Array of exercises with adjusted times
     */
    convertExercisesDateFromServer(exercises?: Exercise[]): Exercise[] {
        const convertedExercises: Exercise[] = [];
        if (exercises && exercises.length > 0) {
            exercises.forEach((exercise: Exercise) => {
                const convertedExercise = this.convertExerciseDateFromServer(exercise);
                if (convertedExercise) {
                    convertedExercises.push(convertedExercise);
                }
            });
        }
        return convertedExercises;
    }

    /**
     * Converts all dates of a client-exercise to the server timezone
     * @param { Exercise } exercise - Exercise from client whose date is adjusted
     */
    convertDateFromClient<E extends Exercise>(exercise: E): E {
        return Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate && dayjs(exercise.releaseDate).isValid() ? dayjs(exercise.releaseDate).toJSON() : undefined,
            dueDate: exercise.dueDate && dayjs(exercise.dueDate).isValid() ? dayjs(exercise.dueDate).toJSON() : undefined,
            assessmentDueDate: exercise.assessmentDueDate && dayjs(exercise.assessmentDueDate).isValid() ? dayjs(exercise.assessmentDueDate).toJSON() : undefined,
        });
    }

    /**
     * Replace dates in http-response including an exercise with the corresponding client time
     * @param { ERT } res - Response from server including one exercise
     */
    convertDateFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? dayjs(res.body.releaseDate) : undefined;
            res.body.dueDate = res.body.dueDate ? dayjs(res.body.dueDate) : undefined;
            res.body.assessmentDueDate = res.body.assessmentDueDate ? dayjs(res.body.assessmentDueDate) : undefined;
            res.body.studentParticipations = this.participationService.convertParticipationsDateFromServer(res.body.studentParticipations);
        }
        return res;
    }

    /**
     * Look up permissions and add/replace isAtLeastInstructor, isAtLeastEditor and isAtLeastTutor to http request containing a course
     * @param { ERT } res - Response from server including a course
     */
    checkPermission<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.course) {
            res.body.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(res.body.course);
            res.body.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(res.body.course);
            res.body.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(res.body.course);
        }
        return res;
    }

    /**
     * Replace dates in http-response including an array of exercises with the corresponding client time
     * @param { EART } res - Response from server including an array of exercise
     */
    convertDateArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: E) => {
                this.convertExerciseDateFromServer(exercise);
            });
        }
        return res;
    }

    /**
     * Converts the exercise category json string into ExerciseCategory objects (if it exists).
     * @param res the response
     */
    convertExerciseCategoriesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.categories) {
            this.parseExerciseCategories(res.body);
        }
        return res;
    }

    /**
     * Converts an exercises' categories into a json string (to send them to the server). Does nothing if no categories exist
     * @param exercise the exercise
     */
    stringifyExerciseCategories(exercise: Exercise) {
        return exercise.categories?.map((category) => JSON.stringify(category) as ExerciseCategory);
    }

    /**
     * Converts the exercise category json strings into ExerciseCategory objects (if it exists).
     * @param res the response
     */
    convertExerciseCategoryArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: E) => this.parseExerciseCategories(exercise));
        }
        return res;
    }

    /**
     * Parses the exercise categories JSON string into ExerciseCategory objects.
     * @param exercise - the exercise
     */
    parseExerciseCategories(exercise: Exercise) {
        if (exercise.categories) {
            exercise.categories = exercise.categories.map((category) => JSON.parse(category as string) as ExerciseCategory);
        }
    }

    /**
     * Create Array of exercise categories from array of strings
     * @param { string[] } categories that are converted to categories
     */
    convertExerciseCategoriesAsStringFromServer(categories: string[]): ExerciseCategory[] {
        return categories.map((category) => JSON.parse(category));
    }

    /**
     * Prepare client-exercise to be uploaded to the server
     * @param { Exercise } exercise - Exercise that will be modified
     */
    convertExerciseForServer<E extends Exercise>(exercise: E): Exercise {
        let copy = Object.assign(exercise, {});
        copy = this.convertDateFromClient(copy);
        copy.categories = this.stringifyExerciseCategories(copy);
        if (copy.course) {
            copy.course.exercises = [];
            copy.course.lectures = [];
        }
        copy.studentParticipations = [];
        return copy;
    }

    /**
     * Get the "exerciseId" exercise with data useful for tutors.
     * @param { number } exerciseId - Id of exercise to retrieve
     */
    getForTutors(exerciseId: number): Observable<HttpResponse<Exercise>> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}/for-assessment-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExerciseEntityResponse(res)));
    }

    /**
     * Retrieve a collection of useful statistics for the tutor exercise dashboard of the exercise with the given exerciseId
     * @param { number } exerciseId - Id of exercise to retrieve the stats for
     */
    getStatsForTutors(exerciseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${exerciseId}/stats-for-assessment-dashboard`, { observe: 'response' });
    }

    /**
     * Retrieves useful statistics for course exercises
     *
     * Gets the {@link CourseExerciseStatisticsDTO} for each exercise proved in <code>exerciseIds</code>. Either the results of the last submission or the results of the last rated
     * submission are considered for a student/team, depending on the value of <code>onlyConsiderRatedResults</code>
     * @param onlyConsiderRatedResults - either the results of the last submission or the results of the last rated submission are considered
     * @param exerciseIds - list of exercise ids (must be belong to the same course)
     */
    getCourseExerciseStatistics(exerciseIds: number[], onlyConsiderRatedResults: boolean): Observable<HttpResponse<CourseExerciseStatisticsDTO[]>> {
        let params = new HttpParams();
        params = params.append('exerciseIds', exerciseIds.join(', '));
        params = params.append('onlyConsiderRatedResults', onlyConsiderRatedResults.toString());
        return this.http.get<CourseExerciseStatisticsDTO[]>(`${this.resourceUrl}/exercises/course-exercise-statistics`, {
            params,
            observe: 'response',
        });
    }

    /**
     * Makes sure that bonus points are zero and respect the constraint by includedInOverallScore
     * @param exercise exercise for which to set the bonus points
     */
    setBonusPointsConstrainedByIncludedInOverallScore(exercise: Exercise) {
        if (exercise.bonusPoints === undefined || exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY) {
            exercise.bonusPoints = 0;
        }
        return exercise;
    }

    isIncludedInScore(exercise: Exercise | undefined) {
        if (!exercise?.includedInOverallScore) {
            return '';
        }
        switch (exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.translateService.instant('artemisApp.exercise.bonus');
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.translateService.instant('artemisApp.exercise.yes');
            case IncludedInOverallScore.NOT_INCLUDED:
                return this.translateService.instant('artemisApp.exercise.no');
        }
    }

    toggleSecondCorrection(exerciseId: number): Observable<Boolean> {
        return this.http.put<boolean>(`${this.resourceUrl}/${exerciseId}/toggle-second-correction`, { observe: 'response' });
    }

    /**
     * This method bundles recurring conversion steps for Exercise EntityResponses.
     * @param exerciseRes
     */
    public processExerciseEntityResponse(exerciseRes: EntityResponseType): EntityResponseType {
        this.convertDateFromServer(exerciseRes);
        this.convertExerciseCategoriesFromServer(exerciseRes);
        this.setAccessRightsExerciseEntityResponseType(exerciseRes);
        return exerciseRes;
    }

    /**
     * This method bundles recurring conversion steps for Exercise EntityArrayResponses.
     * @param exerciseResArray
     */
    public processExerciseEntityArrayResponse(exerciseResArray: EntityArrayResponseType): EntityArrayResponseType {
        this.convertDateArrayFromServer(exerciseResArray);
        this.convertExerciseCategoryArrayFromServer(exerciseResArray);
        this.setAccessRightsExerciseEntityArrayResponseType(exerciseResArray);
        return exerciseResArray;
    }

    public setAccessRightsExerciseEntityArrayResponseType(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((exercise: Exercise) => {
                this.accountService.setAccessRightsForExerciseAndReferencedCourse(exercise);
            });
        }
        return res;
    }

    public setAccessRightsExerciseEntityResponseType(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.accountService.setAccessRightsForExerciseAndReferencedCourse(res.body as Exercise);
        }
        return res;
    }
}

@Injectable({ providedIn: 'root' })
export class ExerciseLtiConfigurationService {
    private resourceUrl = SERVER_API_URL + 'api/lti/configuration';

    constructor(private http: HttpClient) {}

    /**
     * Load exercise with exerciseId from server
     * @param { number } exerciseId - Id of exercise that is loaded
     */
    find(exerciseId: number): Observable<HttpResponse<LtiConfiguration>> {
        return this.http.get<LtiConfiguration>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }
}
