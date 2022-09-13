import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, ParticipationStatus } from 'app/entities/exercise.model';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { ParticipationService } from '../participation/participation.service';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { LtiConfiguration } from 'app/entities/lti-configuration.model';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { User } from 'app/core/user/user.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';

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

    constructor(
        private http: HttpClient,
        private participationService: ParticipationService,
        private accountService: AccountService,
        private translateService: TranslateService,
        private entityTitleService: EntityTitleService,
    ) {}

    /**
     * Persist a new exercise
     * @param { Exercise } exercise - Exercise that will be persisted
     * return
     */
    create(exercise: Exercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseDatesFromClient(exercise);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http.post<Exercise>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processExerciseEntityResponse(res)));
    }

    /**
     * Update existing exercise
     * @param { Exercise } exercise - Exercise that will be updated
     */
    update(exercise: Exercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseDatesFromClient(exercise);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http.put<Exercise>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processExerciseEntityResponse(res)));
    }

    /**
     * Validates if the dates are correct
     */
    validateDate(exercise: Exercise) {
        exercise.dueDateError = this.hasDueDateError(exercise);
        exercise.assessmentDueDateError = this.hasAssessmentDueDateError(exercise);

        exercise.exampleSolutionPublicationDateError = this.hasExampleSolutionPublicationDateError(exercise);
        exercise.exampleSolutionPublicationDateWarning = this.hasExampleSolutionPublicationDateWarning(exercise);
    }

    hasDueDateError(exercise: Exercise) {
        return exercise.releaseDate && exercise.dueDate ? dayjs(exercise.dueDate).isBefore(exercise.releaseDate) : false;
    }

    private hasAssessmentDueDateError(exercise: Exercise) {
        if (exercise.releaseDate && exercise.assessmentDueDate) {
            if (exercise.dueDate) {
                return dayjs(exercise.assessmentDueDate).isBefore(exercise.dueDate) || dayjs(exercise.assessmentDueDate).isBefore(exercise.releaseDate);
            } else {
                return true;
            }
        }

        if (exercise.assessmentDueDate) {
            if (exercise.dueDate) {
                return dayjs(exercise.assessmentDueDate).isBefore(exercise.dueDate);
            } else {
                return true;
            }
        }
        return false;
    }

    hasExampleSolutionPublicationDateError(exercise: Exercise) {
        if (exercise.exampleSolutionPublicationDate) {
            return (
                dayjs(exercise.exampleSolutionPublicationDate).isBefore(exercise.releaseDate || null) ||
                (dayjs(exercise.exampleSolutionPublicationDate).isBefore(exercise.dueDate || null) && exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED)
            );
        }
        return false;
    }

    hasExampleSolutionPublicationDateWarning(exercise: Exercise) {
        if (exercise.exampleSolutionPublicationDate && !dayjs(exercise.exampleSolutionPublicationDate).isSameOrAfter(exercise.dueDate || null)) {
            if (!exercise.dueDate || exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                return true;
            }
        }
        return false;
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
     * Get exercise details including all results for the currently logged-in user
     * @param { number } exerciseId - Id of the exercise to get the repos from
     */
    getExerciseDetails(exerciseId: number): Observable<EntityResponseType> {
        return this.http.get<Exercise>(`${this.resourceUrl}/${exerciseId}/details`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                if (res.body) {
                    // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
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
        // but only if the quiz is synchronized as quizzes in other modes are generally longer running and would just clog up the display all the time
        const nextQuizExercises = exercises?.filter((exercise: QuizExercise) => exercise.quizMode === QuizMode.SYNCHRONIZED && !exercise.quizEnded);
        return (
            // 1st priority is an active quiz
            nextQuizExercises?.find((exercise: QuizExercise) => this.isActiveQuiz(exercise)) ||
            // 2nd priority is a visible quiz
            nextQuizExercises?.find((exercise: QuizExercise) => exercise.visibleToStudents) ||
            // 3rd priority is the next due exercise
            exercises?.find((exercise) => {
                const studentParticipation = student ? exercise.studentParticipations?.find((participation) => participation.student?.id === student?.id) : undefined;
                const dueDate = getExerciseDueDate(exercise, studentParticipation);
                return dueDate && dayjs().isBefore(dueDate) && dayjs().add(delayInHours, 'hours').isSameOrAfter(dueDate);
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
    static convertExerciseDatesFromServer(exercise?: Exercise) {
        if (exercise) {
            exercise.releaseDate = convertDateFromServer(exercise.releaseDate);
            exercise.dueDate = convertDateFromServer(exercise.dueDate);
            exercise.assessmentDueDate = convertDateFromServer(exercise.assessmentDueDate);
            exercise.studentParticipations = ParticipationService.convertParticipationArrayDatesFromServer(exercise.studentParticipations);
        }
        return exercise;
    }

    /**
     * Converts all dates of server-exercises to the client timezone
     * @param { Exercise[] } exercises - Array of server-exercises whose date are adjusted
     * @returns { Exercise[] } - Array of exercises with adjusted times
     */
    static convertExercisesDateFromServer(exercises?: Exercise[]): Exercise[] {
        const convertedExercises: Exercise[] = [];
        if (exercises && exercises.length > 0) {
            exercises.forEach((exercise: Exercise) => {
                const convertedExercise = ExerciseService.convertExerciseDatesFromServer(exercise);
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
    static convertExerciseDatesFromClient<E extends Exercise>(exercise: E): E {
        return Object.assign({}, exercise, {
            releaseDate: convertDateFromClient(exercise.releaseDate),
            dueDate: convertDateFromClient(exercise.dueDate),
            assessmentDueDate: convertDateFromClient(exercise.assessmentDueDate),
            exampleSolutionPublicationDate: convertDateFromClient(exercise.exampleSolutionPublicationDate),
        });
    }

    /**
     * Replace dates in http-response including an exercise with the corresponding client time.
     * @param res - Response from server including one exercise
     */
    static convertExerciseResponseDatesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.releaseDate = convertDateFromServer(res.body.releaseDate);
            res.body.dueDate = convertDateFromServer(res.body.dueDate);
            res.body.assessmentDueDate = convertDateFromServer(res.body.assessmentDueDate);
            res.body.exampleSolutionPublicationDate = convertDateFromServer(res.body.exampleSolutionPublicationDate);
            res.body.studentParticipations = ParticipationService.convertParticipationArrayDatesFromServer(res.body.studentParticipations);
        }
        return res;
    }

    /**
     * Replace dates in http-response including an array of exercises with the corresponding client time
     * @param res - Response from server including an array of exercise
     */
    static convertExerciseArrayDatesFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: E) => {
                ExerciseService.convertExerciseDatesFromServer(exercise);
            });
        }
        return res;
    }

    /**
     * Converts the exercise category json string into ExerciseCategory objects (if it exists).
     * @param res the response
     */
    static convertExerciseCategoriesFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.categories) {
            ExerciseService.parseExerciseCategories(res.body);
        }
        return res;
    }

    /**
     * Converts an exercises' categories into a json string (to send them to the server). Does nothing if no categories exist
     * @param exercise the exercise
     */
    static stringifyExerciseCategories(exercise: Exercise) {
        return exercise.categories?.map((category) => JSON.stringify(category) as ExerciseCategory);
    }

    /**
     * Converts the exercise category json strings into ExerciseCategory objects (if it exists).
     * @param res the response
     */
    static convertExerciseCategoryArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: E) => ExerciseService.parseExerciseCategories(exercise));
        }
        return res;
    }

    /**
     * Parses the exercise categories JSON string into ExerciseCategory objects.
     * @param exercise - the exercise
     */
    static parseExerciseCategories(exercise?: Exercise) {
        if (exercise?.categories) {
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
    static convertExerciseFromClient<E extends Exercise>(exercise: E): Exercise {
        let copy = Object.assign(exercise, {});
        copy = ExerciseService.convertExerciseDatesFromClient(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
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
     * Makes sure that bonus points are zero and respect the constraint by includedInOverallScore
     * @param exercise exercise for which to set the bonus points
     */
    static setBonusPointsConstrainedByIncludedInOverallScore(exercise: Exercise) {
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
        ExerciseService.convertExerciseResponseDatesFromServer(exerciseRes);
        ExerciseService.convertExerciseCategoriesFromServer(exerciseRes);
        this.setAccessRightsExerciseEntityResponseType(exerciseRes);
        this.sendExerciseTitleToTitleService(exerciseRes?.body);
        return exerciseRes;
    }

    /**
     * This method bundles recurring conversion steps for Exercise EntityArrayResponses.
     * @param exerciseResArray
     */
    public processExerciseEntityArrayResponse(exerciseResArray: EntityArrayResponseType): EntityArrayResponseType {
        ExerciseService.convertExerciseArrayDatesFromServer(exerciseResArray);
        ExerciseService.convertExerciseCategoryArrayFromServer(exerciseResArray);
        this.setAccessRightsExerciseEntityArrayResponseType(exerciseResArray);
        exerciseResArray?.body?.forEach(this.sendExerciseTitleToTitleService.bind(this));
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

    public sendExerciseTitleToTitleService(exercise: Exercise | undefined | null) {
        this.entityTitleService.setTitle(EntityType.EXERCISE, [exercise?.id], exercise?.title);
        if (exercise?.course) {
            this.entityTitleService.setTitle(EntityType.COURSE, [exercise.course.id], exercise.course.title);
        }
    }

    public getLatestDueDate(exerciseId: number): Observable<dayjs.Dayjs | undefined> {
        return this.http
            .get<dayjs.Dayjs>(`${this.resourceUrl}/${exerciseId}/latest-due-date`, { observe: 'response' })
            .pipe(map((res: HttpResponse<dayjs.Dayjs>) => (res.body ? dayjs(res.body) : undefined)));
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
