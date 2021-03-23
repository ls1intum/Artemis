import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import * as moment from 'moment';
import { Exercise, ExerciseCategory, ExerciseType, IncludedInOverallScore, ParticipationStatus } from 'app/entities/exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ParticipationService } from '../participation/participation.service';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { LtiConfiguration } from 'app/entities/lti-configuration.model';
import { CourseExerciseStatisticsDTO } from 'app/exercises/shared/exercise/exercise-statistics-dto.model';
import { TranslateService } from '@ngx-translate/core';

export type EntityResponseType = HttpResponse<Exercise>;
export type EntityArrayResponseType = HttpResponse<Exercise[]>;

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
        return this.http
            .post<Exercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /**
     * Update existing exercise
     * @param { Exercise } exercise - Exercise that will be updated
     */
    update(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .put<Exercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /**
     * Validates if the date is correct
     */
    validateDate(exercise: Exercise) {
        exercise.dueDateError = exercise.releaseDate && exercise.dueDate ? !exercise.dueDate.isAfter(exercise.releaseDate) : false;

        exercise.assessmentDueDateError =
            exercise.assessmentDueDate && exercise.releaseDate
                ? !exercise.assessmentDueDate.isAfter(exercise.releaseDate)
                : exercise.assessmentDueDate && exercise.dueDate
                ? !exercise.assessmentDueDate.isAfter(exercise.dueDate)
                : false;
    }

    /**
     * Get exercise with exerciseId from server
     * @param { number } exerciseId - Exercise that should be loaded
     */
    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res))
            .map((res: EntityResponseType) => this.checkPermission(res));
    }

    findExamExercise(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/examExercise/${exerciseId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res))
            .map((res: EntityResponseType) => this.checkPermission(res));
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

    /**
     * Get exercise details including all results for the currently logged in user
     * @param { number } exerciseId - Id of the exercise to get the repos from
     */
    getExerciseDetails(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}/details`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res))
            .map((res: EntityResponseType) => {
                if (res.body) {
                    // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
                    if (res.body.exerciseHints === undefined) {
                        res.body.exerciseHints = [];
                    }
                    if (res.body.studentQuestions === undefined) {
                        res.body.studentQuestions = [];
                    }
                }
                return res;
            })
            .map((res: EntityResponseType) => this.checkPermission(res));
    }

    getUpcomingExercises(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exercise[]>(`${this.resourceUrl}/upcoming`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * Return all exercises of input exercises that are due in delayInDays or 7 days if not specified
     * @param { Exercise[] } exercises - Considered exercises
     * @param { number} delayInDays - If set, amount of days that are considered
     */
    getNextExerciseForDays(exercises?: Exercise[], delayInDays = 7) {
        if (!exercises) {
            return undefined;
        }
        return exercises.find((exercise) => {
            const dueDate = exercise.dueDate!;
            return moment().isBefore(dueDate) && moment().add(delayInDays, 'day').isSameOrAfter(dueDate);
        })!;
    }

    /**
     * Returns an active quiz, a visible quiz or an exercise due in delayInHours or 12 hours if not specified
     * @param { Exercise[] } exercises - Considered exercises
     * @param { number} delayInHours - If set, amount of hours that are considered
     */
    getNextExerciseForHours(exercises?: Exercise[], delayInHours = 12) {
        // check for quiz exercise in order to prioritize before other exercise types
        const nextQuizExercises = exercises?.filter((exercise: QuizExercise) => exercise.type === ExerciseType.QUIZ && !exercise.ended);
        return (
            // 1st priority is an active quiz
            nextQuizExercises?.find((exercise: QuizExercise) => this.isActiveQuiz(exercise)) ||
            // 2nd priority is a visible quiz
            nextQuizExercises?.find((exercise: QuizExercise) => exercise.isVisibleBeforeStart) ||
            // 3rd priority is the next due exercise
            exercises?.find((exercise) => {
                const dueDate = exercise.dueDate!;
                return moment().isBefore(dueDate) && moment().add(delayInHours, 'hours').isSameOrAfter(dueDate);
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
            exercise.releaseDate = exercise.releaseDate ? moment(exercise.releaseDate) : undefined;
            exercise.dueDate = exercise.dueDate ? moment(exercise.dueDate) : undefined;
            exercise.assessmentDueDate = exercise.assessmentDueDate ? moment(exercise.assessmentDueDate) : undefined;
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
            releaseDate: exercise.releaseDate && moment(exercise.releaseDate).isValid() ? moment(exercise.releaseDate).toJSON() : undefined,
            dueDate: exercise.dueDate && moment(exercise.dueDate).isValid() ? moment(exercise.dueDate).toJSON() : undefined,
            assessmentDueDate: exercise.assessmentDueDate && moment(exercise.assessmentDueDate).isValid() ? moment(exercise.assessmentDueDate).toJSON() : undefined,
        });
    }

    /**
     * Replace dates in http-response including an exercise with the corresponding client time
     * @param { ERT } res - Response from server including one exercise
     */
    convertDateFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
            res.body.dueDate = res.body.dueDate ? moment(res.body.dueDate) : undefined;
            res.body.assessmentDueDate = res.body.assessmentDueDate ? moment(res.body.assessmentDueDate) : undefined;
            res.body.studentParticipations = this.participationService.convertParticipationsDateFromServer(res.body.studentParticipations);
        }
        return res;
    }

    /**
     * Look up permissions and add/replace isAtLeastInstuctor and isAtLeastTutor to http request containing a course
     * @param { ERT } res - Response from server including a course
     */
    checkPermission<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.course) {
            res.body.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(res.body.course);
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
     * Create Array of exercise categories from exercise
     * @param { Exercise } exercise whos categories should be converted
     */
    convertExerciseCategoriesFromServer(exercise: Exercise): ExerciseCategory[] {
        if (!exercise || !exercise.categories) {
            return [];
        }
        return exercise.categories.map((el) => JSON.parse(el));
    }

    /**
     * Create Array of exercise categories from array of strings
     * @param { string[] } categories that are converted to categories
     */
    convertExerciseCategoriesAsStringFromServer(categories: string[]): ExerciseCategory[] {
        return categories.map((el) => JSON.parse(el));
    }

    /**
     * Prepare client-exercise to be uploaded to the server
     * @param { Exercise } exercise - Exercise that will be modified
     */
    convertExerciseForServer<E extends Exercise>(exercise: E): Exercise {
        let copy = Object.assign(exercise, {});
        copy = this.convertDateFromClient(copy);
        if (copy.course) {
            copy.course.exercises = [];
            copy.course.lectures = [];
        }
        copy.studentParticipations = [];
        return copy;
    }

    /**
     * Get the "exerciseId" exercise with data useful for tutors.
     * @param { number } exerciseId - Id of exercise to retreive
     */
    getForTutors(exerciseId: number): Observable<HttpResponse<Exercise>> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}/for-assessment-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Retrieve a collection of useful statistics for the tutor exercise dashboard of the exercise with the given exerciseId
     * @param { number } exerciseId - Id of exercise to retreive the stats for
     */
    getStatsForTutors(exerciseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${exerciseId}/stats-for-assessment-dashboard`, { observe: 'response' });
    }

    /**
     * Retrieve a collection of useful statistics for the instructor exercise dashboard of the exercise with the given exerciseId
     * @param { number } exerciseId - Id of exercise to retreive the stats for
     */
    getStatsForInstructors(exerciseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${exerciseId}/stats-for-instructor-dashboard`, { observe: 'response' });
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
