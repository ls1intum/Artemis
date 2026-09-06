import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { CoursesForDashboardDTO } from 'app/course/shared/entities/courses-for-dashboard-dto';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { filter, map, tap } from 'rxjs/operators';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { User, UserNameAndLoginDTO, UserPublicInfoDTO } from 'app/account/user/user.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { AccountService } from 'app/core/auth/account.service';
import { createRequestOption } from 'app/foundation/util/request.util';
import { Submission, reconnectSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { CourseManagementDetailViewDto } from 'app/course/shared/entities/course-management-detail-view-dto.model';
import { convertDateFromClient } from 'app/foundation/util/date.utils';
import { objectToJsonBlob } from 'app/foundation/util/blob-util';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { CourseForDashboardDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { CourseAccessStateDTO } from 'app/course/shared/entities/course-access-state-dto';
import { CourseForOverviewDTO, courseFromOverviewDTO } from 'app/course/shared/entities/course-for-overview-dto';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ExerciseType, ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { OnlineCourseDtoModel } from 'app/lti/shared/entities/online-course-dto.model';
import { CourseForArchiveDTO } from '../../shared/entities/course-for-archive-dto';
import { addPublicFilePrefix } from 'app/app.constants';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { convertTutorialGroupArrayDatesFromServer, convertTutorialGroupsConfigurationDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { toCourseUpdateDTO } from 'app/course/shared/entities/course-update-dto.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

export type RoleGroup = 'tutors' | 'students' | 'instructors' | 'editors';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CourseGradeInformationDTO {
    gradeScores!: GradeScoreDTO[];
    students!: StudentGradeDTO[];
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class StudentGradeDTO {
    id!: number;
    login!: string;
    firstName!: string;
    lastName!: string;
    name!: string;
    registrationNumber?: string;
    email!: string;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class GradeScoreDTO {
    participationId!: number;
    userId!: number;
    exerciseId!: number;
    score!: number;
    presentationScore!: number;
}

@Injectable({ providedIn: 'root' })
export class CourseManagementService implements OnDestroy {
    private http = inject(HttpClient);
    private courseStorageService = inject(CourseStorageService);
    private lectureService = inject(LectureService);
    private accountService = inject(AccountService);
    private entityTitleService = inject(EntityTitleService);
    private scoresStorageService = inject(ScoresStorageService);
    private courseNotificationService = inject(CourseNotificationService);
    private localStorageService = inject(LocalStorageService);

    private resourceUrl = 'api/course/courses';

    private coursesForNotifications: BehaviorSubject<Course[] | undefined> = new BehaviorSubject<Course[] | undefined>(undefined);

    private fetchingCoursesForNotifications = false;

    private stateGeneration = 0;
    private currentUserId?: number;
    private authenticationStateSubscription: Subscription;

    constructor() {
        this.currentUserId = this.accountService.userIdentity()?.id;
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.resetState();
            }
        });
    }

    ngOnDestroy(): void {
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * Clears the cached courses-for-notifications list. Called on logout / user change so the next
     * user does not see the previous user's enrolled courses. The generation counter ensures
     * in-flight dashboard fetches don't repopulate the subject after reset.
     */
    private resetState(): void {
        this.stateGeneration++;
        this.fetchingCoursesForNotifications = false;
        this.coursesForNotifications.next(undefined);
    }

    /**
     * updates a course using a PUT request
     * @param courseId - the id of the course to be updated
     * @param courseUpdate - the updates to the course
     * @param courseImage - the course icon file
     */
    update(courseId: number, courseUpdate: Course, courseImage?: Blob): Observable<EntityResponseType> {
        const dto = toCourseUpdateDTO(courseUpdate);
        const formData = new FormData();
        formData.append('course', objectToJsonBlob(dto));
        if (courseImage) {
            // The image was cropped by us and is a blob, so we need to set a placeholder name for the server check
            formData.append('file', courseImage, 'placeholderName.png');
        }
        return this.http
            .put<Course>(`${this.resourceUrl}/${courseId}`, formData, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    /**
     * updates the online course configuration of a course using a PUT request
     * @param courseId - the id of the course to be updated
     * @param onlineCourseConfiguration - the updates to the online course configuration
     */
    updateOnlineCourseConfiguration(courseId: number, onlineCourseConfiguration: OnlineCourseConfiguration): Observable<EntityResponseType> {
        return this.http.put<OnlineCourseConfiguration>(`api/lti/courses/${courseId}/online-course-configuration`, onlineCourseConfiguration, { observe: 'response' });
    }

    findAllOnlineCoursesWithRegistrationId(clientId: string): Observable<OnlineCourseDtoModel[]> {
        const params = new HttpParams().set('clientId', '' + clientId);
        return this.http.get<OnlineCourseDtoModel[]>('api/lti/courses/for-lti-dashboard', { params });
    }

    /**
     * finds the course with the provided unique identifier
     * @param courseId - the id of the course to be found
     */
    find(courseId: number): Observable<EntityResponseType> {
        return this.http.get<Course>(`${this.resourceUrl}/${courseId}`, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    /**
     * gets course information required for the course management detail page
     * @param courseId the id of the course of which the detailed data should be fetched
     */
    getCourseStatisticsForDetailView(courseId: number): Observable<HttpResponse<CourseManagementDetailViewDto>> {
        return this.http
            .get<CourseManagementDetailViewDto>(`${this.resourceUrl}/${courseId}/management-detail`, { observe: 'response' })
            .pipe(filter((res: HttpResponse<CourseManagementDetailViewDto>) => !!res.body));
    }

    /**
     * gets the active users for the line chart in the detail view
     * @param courseId the id of the course of which the statistics should be fetched
     * @param periodIndex the period of the statistics we want to have
     * @param periodSize the size of the statistics-period to be fetched
     */
    getStatisticsData(courseId: number, periodIndex: number, periodSize?: number): Observable<number[]> {
        const params: Record<string, number> = { periodIndex };
        if (periodSize) {
            params.periodSize = periodSize;
        }
        return this.http.get<number[]>(`${this.resourceUrl}/${courseId}/statistics`, { params });
    }

    /**
     * get the active users for the lifetime overview of the line chart in the detail view
     * @param courseId the id of the course of which the statistics should be fetched
     */
    getStatisticsForLifetimeOverview(courseId: number): Observable<number[]> {
        return this.http.get<number[]>(`${this.resourceUrl}/${courseId}/statistics-lifetime-overview`);
    }

    /**
     * finds the course with the provided unique identifier together with its exercises
     * @param courseId - the id of the course to be found
     */
    findWithExercises(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/with-exercises`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    /**
     * finds the course with the provided unique identifier together with its exercises
     * @param courseId - the id of the course to be found
     */
    findWithExercisesAndLecturesAndCompetencies(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/with-exercises-lectures-competencies`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    /**
     * finds a course with the given id and eagerly loaded organizations
     * @param courseId the id of the course to be found
     */
    findWithOrganizations(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/with-organizations`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    findAllForDropdown(): Observable<HttpResponse<Course[]>> {
        return this.http.get<Course[]>(`${this.resourceUrl}/for-dropdown`, { observe: 'response' });
    }

    /**
     * finds all courses using a GET request
     */
    findAllForDashboard(): Observable<HttpResponse<CoursesForDashboardDTO>> {
        this.fetchingCoursesForNotifications = true;
        const generation = this.stateGeneration;
        return this.http.get<CoursesForDashboardDTO>(`${this.resourceUrl}/for-dashboard`, { observe: 'response' }).pipe(
            map((res: HttpResponse<CoursesForDashboardDTO>) => {
                if (this.stateGeneration !== generation) return res;
                if (res.body) {
                    const courses: Course[] = [];
                    res.body.courses?.forEach((courseForDashboardDTO) => {
                        if (courseForDashboardDTO.course.id) {
                            this.courseNotificationService.updateNotificationCountMap(courseForDashboardDTO.course.id, courseForDashboardDTO.courseNotificationCount);

                            // Setting the helper attribute in the course so we can use it in the course overview guard.
                            courseForDashboardDTO.course.irisEnabledInCourse = courseForDashboardDTO.irisEnabledInCourse;
                        }
                        courses.push(courseForDashboardDTO.course);
                        this.saveScoresInStorage(courseForDashboardDTO);
                    });
                    // Replace the CourseForDashboardDTOs in the response body with the normal courses to enable further processing.
                    const courseResponse = res.clone({ body: courses });
                    this.processCourseEntityArrayResponseType(courseResponse);
                    this.setCoursesForNotifications(courseResponse, generation);
                    this.courseStorageService.setCourses(courseResponse.body !== null ? courseResponse.body : undefined);
                }
                return res;
            }),
        );
    }

    /**
     * Asks whether the current user already has access to a course.
     *
     * The cheapest question the server can answer about a course: one indexed existence check, no entity loading, and a
     * single boolean back. A user without access gets `false` rather than an error, so the enrollment page can ask
     * without provoking a global error alert for the case it exists to handle.
     *
     * @param courseId the course to check
     */
    hasAccessToCourse(courseId: number): Observable<boolean> {
        return this.http.get<CourseAccessStateDTO>(`${this.resourceUrl}/${courseId}/access-state`).pipe(map((state) => state.hasAccess));
    }

    /**
     * Finds one course with all of its content using a GET request.
     *
     * @deprecated The web client no longer uses this: the course overview loads {@link findCourseForOverview} and each
     * tab loads what it needs. The endpoint stays for the iOS, Android and VS Code clients. Do not add new callers.
     * @param courseId the course to fetch
     */
    findOneForDashboard(courseId: number): Observable<EntityResponseType> {
        const params = new HttpParams();
        return this.http.get<CourseForDashboardDTO>(`${this.resourceUrl}/${courseId}/for-dashboard`, { params, observe: 'response' }).pipe(
            map((res: HttpResponse<CourseForDashboardDTO>) => {
                if (res.body) {
                    const courseForDashboardDTO: CourseForDashboardDTO = res.body;
                    if (courseForDashboardDTO.course.id) {
                        this.courseNotificationService.updateNotificationCountMap(courseForDashboardDTO.course.id, courseForDashboardDTO.courseNotificationCount);

                        // Expose the per-course Iris enablement on the cached course object for the overview UI (the guard uses the dedicated access endpoint instead).
                        courseForDashboardDTO.course.irisEnabledInCourse = courseForDashboardDTO.irisEnabledInCourse;
                    }
                    this.saveScoresInStorage(courseForDashboardDTO);

                    // Replace the CourseForDashboardDTO in the response body with the normal course to enable further processing.
                    return res.clone({ body: courseForDashboardDTO.course });
                }
                return res;
            }),
            map((res: EntityResponseType) => this.processCourseEntityResponseType(res)),
            tap((res: EntityResponseType) => this.courseStorageService.updateCourse(res.body !== null ? res.body : undefined)),
        );
    }

    /**
     * Fetches the course itself for the course overview container, without any of its content.
     *
     * This replaces {@link findOneForDashboard} for the web client: exercises, lectures, exams, participations and
     * scores are loaded by the tab that needs them, so entering a course no longer pays for content the user may never
     * open. The result is stored in the {@link CourseStorageService} exactly as before, so everything reading the course
     * from there keeps working.
     *
     * @param courseId the course to fetch
     */
    findCourseForOverview(courseId: number): Observable<EntityResponseType> {
        return this.http.get<CourseForOverviewDTO>(`${this.resourceUrl}/${courseId}/for-overview`, { observe: 'response' }).pipe(
            map((res: HttpResponse<CourseForOverviewDTO>): EntityResponseType => {
                const dto = res.body;
                if (!dto) {
                    // HttpResponse<Course> already allows a null body; rebuild it rather than cast a clone
                    return new HttpResponse<Course>({ headers: res.headers, status: res.status, url: res.url ?? undefined });
                }
                this.courseNotificationService.updateNotificationCountMap(dto.id, dto.courseNotificationCount);
                // Build the course the rest of the pipe and every consumer of the stored course still work with
                return res.clone({ body: courseFromOverviewDTO(dto) });
            }),
            // Still the shared post-processing: it derives the course icon path, the access-right flags and the title
            // service entries that consumers of the stored course read
            map((res: EntityResponseType) => this.processCourseEntityResponseType(res)),
            tap((res: EntityResponseType) => this.storeCoursePreservingLoadedContent(courseId, res.body ?? undefined)),
        );
    }

    /**
     * Stores the lean course without discarding content a per-tab loader has already published on it.
     *
     * The course record and the exercise list are fetched by separate requests that can complete in either order. A
     * plain store would mean that whenever the (fast) course response lands after the (slow) exercise response, the
     * exercises are silently dropped from the stored course and the exercises tab renders empty.
     *
     * @param courseId the course that was requested
     * @param course   the freshly loaded lean course
     */
    private storeCoursePreservingLoadedContent(courseId: number, course?: Course): void {
        if (!course) {
            this.courseStorageService.removeCourse(courseId);
            return;
        }
        // No copy is needed: `course` was just built from the overview projection and is referenced by nothing else, so
        // it can simply be completed before being stored. The exercises are carried over by reference on purpose —
        // live updates mutate those exercise objects in place and the sidebar renders the same objects.
        const alreadyLoadedExercises = course.id !== undefined ? this.courseStorageService.getCourse(course.id)?.exercises : undefined;
        if (alreadyLoadedExercises) {
            course.exercises = alreadyLoadedExercises;
        }
        this.courseStorageService.updateCourse(course);
    }

    /**
     * Fetches the user's exercises for a course together with the derived scores, and stores the scores in the
     * {@link ScoresStorageService}. Prefer {@link CourseOverviewExercisesService} over calling this directly — it shares
     * one response between the exercises tab and the statistics tab.
     *
     * @param courseId the course to fetch the exercises for
     */
    findCourseExercisesForOverview(courseId: number): Observable<CourseExercisesForOverviewDTO> {
        return this.http.get<CourseExercisesForOverviewDTO>(`${this.resourceUrl}/${courseId}/exercises-for-overview`).pipe(
            map((dto) => {
                this.saveExerciseScoresInStorage(courseId, dto);
                // Same conversions the course response applies to its exercises, so consumers see identical objects
                dto.exercises = ExerciseService.convertExercisesDateFromServer(dto.exercises) ?? [];
                dto.exercises.forEach((exercise) => ExerciseService.parseExerciseCategories(exercise));
                dto.exercises.forEach((exercise) => this.entityTitleService.setExerciseTitle(exercise));
                return dto;
            }),
        );
    }

    /**
     * Fetches which course overview tabs are available to the current user. Prefer
     * {@link CourseAvailableTabsService} over calling this directly — it shares one response between the course sidebar
     * and the {@link CourseOverviewGuard} so a course visit costs a single request.
     * @param courseId the course to fetch the available tabs for
     */
    getCourseAvailableTabs(courseId: number): Observable<CourseAvailableTabs> {
        return this.http.get<CourseAvailableTabs>(`${this.resourceUrl}/${courseId}/available-tabs`);
    }

    saveScoresInStorage(courseForDashboardDTO: CourseForDashboardDTO) {
        this.saveExerciseScoresInStorage(courseForDashboardDTO.course.id!, courseForDashboardDTO);
    }

    /**
     * Stores the score parts of a course payload. Shared by the (deprecated) for-dashboard response, the courses list
     * and the exercises-for-overview response, which all carry the same score fields.
     *
     * @param courseId the course the scores belong to
     * @param scores   the score fields of the payload
     */
    private saveExerciseScoresInStorage(
        courseId: number,
        scores: Pick<
            CourseForDashboardDTO,
            'totalScores' | 'programmingScores' | 'modelingScores' | 'quizScores' | 'textScores' | 'fileUploadScores' | 'participationResults' | 'achievedPointsPerVariantGroup'
        >,
    ) {
        // Save the total scores in the scores-storage.service.
        this.scoresStorageService.setStoredTotalScores(courseId, scores.totalScores);

        const scoresPerExerciseType: ScoresPerExerciseType = new Map();
        scoresPerExerciseType.set(ExerciseType.PROGRAMMING, scores.programmingScores);
        scoresPerExerciseType.set(ExerciseType.MODELING, scores.modelingScores);
        scoresPerExerciseType.set(ExerciseType.QUIZ, scores.quizScores);
        scoresPerExerciseType.set(ExerciseType.TEXT, scores.textScores);
        scoresPerExerciseType.set(ExerciseType.FILE_UPLOAD, scores.fileUploadScores);
        this.scoresStorageService.setStoredScoresPerExerciseType(courseId, scoresPerExerciseType);

        // Save the participation results in the scores-storage.service.
        this.scoresStorageService.setStoredParticipationResults(scores.participationResults);

        // Save the per-variant-group achieved points (server-computed: capped and plagiarism-adjusted).
        this.scoresStorageService.setStoredAchievedPointsPerVariantGroup(courseId, scores.achievedPointsPerVariantGroup);
    }

    /**
     * finds all participants of the course corresponding to the given unique identifier
     * @param courseId - the id of the course
     */
    findGradeScores(courseId: number): Observable<CourseGradeInformationDTO> {
        return this.http.get<CourseGradeInformationDTO>(`api/assessment/courses/${courseId}/grade-scores`);
    }

    /**
     * finds all results of exercises of the course corresponding to the given unique identifier for the current user
     * @param courseId - the id of the course
     */
    findAllResultsOfCourseForExerciseAndCurrentUser(courseId: number): Observable<Course> {
        return this.http.get<Course>(`${this.resourceUrl}/${courseId}/results`).pipe(
            map((res: Course) => {
                this.accountService.setAccessRightsForCourseAndReferencedExercises(res);
                return res;
            }),
        );
    }

    /**
     * returns the course with the provided unique identifier for the assessment dashboard
     * @param courseId - the id of the course
     */
    getCourseWithInterestingExercisesForTutors(courseId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/${courseId}/for-assessment-dashboard`;
        return this.http.get<Course>(url, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    /**
     * returns the stats of the course with the provided unique identifier for the assessment dashboard
     * @param courseId - the id of the course
     */
    getStatsForTutors(courseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${courseId}/stats-for-assessment-dashboard`, { observe: 'response' });
    }

    /**
     * finds all courses that can be registered to
     */
    findAllForRegistration(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Course[]>(`${this.resourceUrl}/for-enrollment`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)));
    }

    /**
     * finds a single course that can be registered to (with limited information)
     */
    findOneForRegistration(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/for-enrollment`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    /**
     * register to the course with the provided unique identifier using a POST request
     * NB: the body is null, because the server can identify the user anyway
     * @param courseId - the id of the course
     */
    registerForCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${courseId}/enroll`, null, { observe: 'response' });
    }

    /**
     * unenroll from course with the provided unique identifier using a POST request
     * NB: the body is null, because the server can identify the user anyway
     * @param courseId - the id of the course
     */
    unenrollFromCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${courseId}/unenroll`, null, { observe: 'response' });
    }

    /**
     * finds all courses with quiz exercises using a GET request
     */
    getAllCoursesWithQuizExercises(): Observable<EntityArrayResponseType> {
        this.fetchingCoursesForNotifications = true;
        const generation = this.stateGeneration;
        return this.http.get<Course[]>(this.resourceUrl + '/courses-with-quiz', { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res, generation)),
        );
    }

    /**
     * finds all courses for the overview using a GET request
     * @param req a dictionary which is sent as request option along the REST call
     */
    getCourseOverview(req?: Record<string, string | number | boolean>): Observable<HttpResponse<Course[]>> {
        const options = createRequestOption(req);
        this.fetchingCoursesForNotifications = true;
        return this.http.get<Course[]>(`${this.resourceUrl}/course-management-overview`, { params: options, observe: 'response' }).pipe(
            tap((res: HttpResponse<Course[]>) => {
                if (res.body) {
                    res.body.forEach((course) => this.accountService.setAccessRightsForCourse(course));
                }
            }),
        );
    }

    /**
     * Find all courses for the archive using a GET request
     */
    getCoursesForArchive(): Observable<HttpResponse<CourseForArchiveDTO[]>> {
        return this.http.get<CourseForArchiveDTO[]>(`${this.resourceUrl}/for-archive`, { observe: 'response' });
    }

    /**
     * returns all the categories of the course corresponding to the given unique identifier
     * @param courseId - the id of the course
     */
    findAllCategoriesOfCourse(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(`${this.resourceUrl}/${courseId}/categories`, { observe: 'response' });
    }

    /**
     * Returns all users enrolled in the course with the given role.
     * @param courseId - the id of the course
     * @param courseRoleSlug - the role path segment ('students', 'tutors', 'editors', 'instructors')
     */
    getAllUsersInCourseRole(courseId: number, courseRoleSlug: CourseRoleSlug): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`${this.resourceUrl}/${courseId}/${courseRoleSlug}`, { observe: 'response' });
    }

    /**
     * finds users of the course corresponding to the name
     * @param courseId  the id of the course
     * @param name      the term to search users
     */
    searchOtherUsersInCourse(courseId: number, name: string): Observable<HttpResponse<User[]>> {
        let httpParams = new HttpParams();
        httpParams = httpParams.append('nameOfUser', name);
        return this.http.get<User[]>(`${this.resourceUrl}/${courseId}/search-other-users`, { params: httpParams, observe: 'response' });
    }

    searchUsers(courseId: number, loginOrName: string, roles: RoleGroup[]): Observable<HttpResponse<UserPublicInfoDTO[]>> {
        let httpParams = new HttpParams();
        httpParams = httpParams.append('loginOrName', loginOrName);
        httpParams = httpParams.append('roles', roles.join(','));
        return this.http.get<User[]>(`${this.resourceUrl}/${courseId}/users/search`, { observe: 'response', params: httpParams });
    }

    searchMembersForUserMentions(courseId: number, loginOrName: string): Observable<HttpResponse<UserNameAndLoginDTO[]>> {
        let httpParams = new HttpParams();
        httpParams = httpParams.append('loginOrName', loginOrName);
        return this.http.get<User[]>(`${this.resourceUrl}/${courseId}/members/search`, { observe: 'response', params: httpParams });
    }

    /**
     * Search for a student on the server by login or name in the specified course.
     * @param loginOrName The login or name to search for.
     * @param courseId The id of the course to search in.
     * @return Observable<HttpResponse<User[]>> with the list of found users as body.
     */
    searchStudents(courseId: number, loginOrName: string): Observable<HttpResponse<User[]>> {
        // create loginOrName HTTP Param
        let httpParams = new HttpParams();
        httpParams = httpParams.append('loginOrName', loginOrName);
        return this.http.get<User[]>(`${this.resourceUrl}/${courseId}/students/search`, { observe: 'response', params: httpParams });
    }
    /**
     * Downloads the course archive of the specified courseId. Returns an error
     * if the archive does not exist.
     * @param courseId The id of the course
     */
    downloadCourseArchive(courseId: number): void {
        const url = `${this.resourceUrl}/${courseId}/download-archive`;
        window.open(url, '_blank');
    }

    /**
     * Archives the course of the specified courseId.
     * @param courseId The id of the course to archive
     */
    archiveCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/${courseId}/archive`, {}, { observe: 'response' });
    }

    cleanupCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}/cleanup`, { observe: 'response' });
    }

    /**
     * Find all locked submissions of a given course for user
     * @param {number} courseId - The id of the course to be searched for
     */
    findAllLockedSubmissionsOfCourse(courseId: number): Observable<HttpResponse<Submission[]>> {
        return this.http.get<Submission[]>(`${this.resourceUrl}/${courseId}/locked-submissions`, { observe: 'response' }).pipe(
            filter((res) => !!res.body),
            tap((res) => reconnectSubmissions(res.body!)),
        );
    }

    /**
     * Adds a single user to the course with the given role.
     * @param courseId - the id of the course
     * @param courseRoleSlug - the role path segment ('students', 'tutors', 'editors', 'instructors')
     * @param login - login of the user to be added
     */
    addUserToCourseRole(courseId: number, courseRoleSlug: CourseRoleSlug, login: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${courseId}/${courseRoleSlug}/${login}`, {}, { observe: 'response' });
    }

    /**
     * Bulk-adds users to the course with the given role.
     * @param courseId the id of the course
     * @param studentDtos Student DTOs of users to add
     * @param courseRoleSlug the role path segment ('students', 'tutors', 'editors', 'instructors')
     * @return studentDtos of users that were not found in the system
     */
    addUsersToCourseRole(courseId: number, studentDtos: StudentDTO[], courseRoleSlug: string): Observable<HttpResponse<StudentDTO[]>> {
        return this.http.post<StudentDTO[]>(`${this.resourceUrl}/${courseId}/${courseRoleSlug}`, studentDtos, { observe: 'response' });
    }

    /**
     * Removes a single user from the given role in the course.
     * @param courseId - the id of the course
     * @param courseRoleSlug - the role path segment ('students', 'tutors', 'editors', 'instructors')
     * @param login - login of the user to be removed
     */
    removeUserFromCourseRole(courseId: number, courseRoleSlug: CourseRoleSlug, login: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}/${courseRoleSlug}/${login}`, { observe: 'response' });
    }

    /**
     * Gets the cached courses. If there are none, the courses for the current user will be fetched.
     * @returns {BehaviorSubject<Course[] | undefined>}
     */
    getCoursesForNotifications(): BehaviorSubject<Course[] | undefined> {
        const generation = this.stateGeneration;
        // The timeout is set to ensure that the request for retrieving courses
        // here is only made if there was no similar request made before.
        setTimeout(() => {
            if (this.stateGeneration !== generation) return;
            // Retrieve courses if no courses were fetched before and are not queried at the moment.
            if (!this.fetchingCoursesForNotifications && !this.coursesForNotifications.getValue()) {
                this.findAllForNotifications().subscribe({
                    next: (res: HttpResponse<Course[]>) => {
                        if (this.stateGeneration !== generation) return;
                        this.coursesForNotifications.next(res.body || undefined);
                    },
                    error: () => (this.fetchingCoursesForNotifications = false),
                });
            }
        }, 500);
        return this.coursesForNotifications;
    }

    /**
     * This method bundles recurring conversion steps for Course EntityResponses.
     * @param courseRes
     */
    processCourseEntityResponseType(courseRes: EntityResponseType): EntityResponseType {
        this.processCourseIcon(courseRes);
        this.convertTutorialGroupDatesFromServer(courseRes);
        this.convertTutorialGroupConfigurationDateFromServer(courseRes);
        this.convertCourseResponseDateFromServer(courseRes);
        this.setCompetenciesIfNone(courseRes);
        this.setAccessRightsCourseEntityResponseType(courseRes);
        this.convertExerciseCategoriesFromServer(courseRes);
        this.sendCourseTitleAndExerciseTitlesToTitleService(courseRes?.body);
        return courseRes;
    }

    /**
     * THis method adds the public file prefix to the course icon.
     * @param courseRes
     */
    private processCourseIcon(courseRes: EntityResponseType): EntityResponseType {
        if (courseRes.body) {
            courseRes.body.courseIconPath = addPublicFilePrefix(courseRes.body.courseIcon);
        }
        return courseRes;
    }

    /**
     * This method bundles recurring conversion steps for Course processCourseEntityArrayResponseType.
     * @param courseRes
     */
    private processCourseEntityArrayResponseType(courseRes: EntityArrayResponseType): EntityArrayResponseType {
        this.convertTutorialGroupsDatesFromServer(courseRes);
        this.convertTutorialGroupConfigurationsDateFromServer(courseRes);
        this.convertCourseArrayResponseDatesFromServer(courseRes);
        this.convertExerciseCategoryArrayFromServer(courseRes);
        this.setAccessRightsCourseEntityArrayResponseType(courseRes);
        courseRes?.body?.forEach(this.sendCourseTitleAndExerciseTitlesToTitleService.bind(this));
        return courseRes;
    }

    private setCoursesForNotifications(res: EntityArrayResponseType, generation: number = this.stateGeneration): EntityArrayResponseType {
        if (this.stateGeneration !== generation) {
            return res;
        }
        if (res.body) {
            this.coursesForNotifications.next(res.body);
            this.fetchingCoursesForNotifications = false;
        }
        return res;
    }

    static convertCourseDatesFromClient(course: Course): Course {
        // copy of the object
        return cloneWith(course, {
            startDate: convertDateFromClient(course.startDate),
            endDate: convertDateFromClient(course.endDate),
            enrollmentStartDate: convertDateFromClient(course.enrollmentStartDate),
            enrollmentEndDate: convertDateFromClient(course.enrollmentEndDate),
            unenrollmentEndDate: convertDateFromClient(course.unenrollmentEndDate),
        });
    }

    private convertTutorialGroupDatesFromServer(courseRes: EntityResponseType): EntityResponseType {
        if (courseRes.body?.tutorialGroups) {
            courseRes.body.tutorialGroups = convertTutorialGroupArrayDatesFromServer(courseRes.body.tutorialGroups);
        }
        return courseRes;
    }

    private convertTutorialGroupsDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                if (course.tutorialGroups) {
                    course.tutorialGroups = convertTutorialGroupArrayDatesFromServer(course.tutorialGroups);
                }
            });
        }
        return res;
    }

    private convertTutorialGroupConfigurationDateFromServer(courseRes: EntityResponseType): EntityResponseType {
        if (courseRes.body?.tutorialGroupsConfiguration) {
            courseRes.body.tutorialGroupsConfiguration = convertTutorialGroupsConfigurationDatesFromServer(courseRes.body.tutorialGroupsConfiguration);
        }
        return courseRes;
    }

    private convertTutorialGroupConfigurationsDateFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                if (course.tutorialGroupsConfiguration) {
                    course.tutorialGroupsConfiguration = convertTutorialGroupsConfigurationDatesFromServer(course.tutorialGroupsConfiguration);
                }
            });
        }
        return res;
    }

    private convertCourseResponseDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.setCourseDates(res.body);
        }
        return res;
    }

    private convertCourseArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => this.setCourseDates(course));
        }
        return res;
    }

    /**
     * Converts the exercise category json string into ExerciseCategory objects (if it exists).
     * @param res the response
     */
    private convertExerciseCategoriesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body && res.body.exercises) {
            res.body.exercises.forEach((exercise) => ExerciseService.parseExerciseCategories(exercise));
        }
        return res;
    }

    /**
     * Converts an array of exercise category json strings into ExerciseCategory objects (if it exists).
     * @param res the response
     */
    private convertExerciseCategoryArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                if (course.exercises) {
                    course.exercises.forEach((exercise) => ExerciseService.parseExerciseCategories(exercise));
                }
            });
        }
        return res;
    }

    private setCourseDates(course: Course) {
        course.startDate = course.startDate ? dayjs(course.startDate) : undefined;
        course.endDate = course.endDate ? dayjs(course.endDate) : undefined;
        course.enrollmentStartDate = course.enrollmentStartDate ? dayjs(course.enrollmentStartDate) : undefined;
        course.enrollmentEndDate = course.enrollmentEndDate ? dayjs(course.enrollmentEndDate) : undefined;
        course.unenrollmentEndDate = course.unenrollmentEndDate ? dayjs(course.unenrollmentEndDate) : undefined;
        course.exercises = ExerciseService.convertExercisesDateFromServer(course.exercises);
        course.lectures = this.lectureService.convertLectureArrayDatesFromServer(course.lectures);
    }

    /**
     * Set the competencies and prerequisites to an empty array if undefined
     * We late distinguish between undefined (not yet fetched) and an empty array (fetched but course has none)
     * @param res The server response containing a course object
     */
    private setCompetenciesIfNone(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.competencies = res.body.competencies || [];
            res.body.prerequisites = res.body.prerequisites || [];
        }
        return res;
    }

    private setAccessRightsCourseEntityArrayResponseType(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                this.accountService.setAccessRightsForCourseAndReferencedExercises(course);
            });
        }
        return res;
    }

    private setAccessRightsCourseEntityResponseType(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.accountService.setAccessRightsForCourseAndReferencedExercises(res.body);
        }
        return res;
    }

    public findAllForNotifications(): Observable<EntityArrayResponseType> {
        this.fetchingCoursesForNotifications = true;
        const generation = this.stateGeneration;
        return this.http.get<Course[]>(`${this.resourceUrl}/for-notifications`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res, generation)),
        );
    }

    sendCourseTitleAndExerciseTitlesToTitleService(course: Course | null | undefined) {
        this.entityTitleService.setTitle(EntityType.COURSE, [course?.id], course?.title);

        course?.exercises?.forEach((exercise) => {
            this.entityTitleService.setExerciseTitle(exercise);
        });
        course?.lectures?.forEach((lecture) => this.entityTitleService.setTitle(EntityType.LECTURE, [lecture.id], lecture.title));
        course?.exams?.forEach((exam) => this.entityTitleService.setTitle(EntityType.EXAM, [exam.id], exam.title));
        course?.organizations?.forEach((org) => this.entityTitleService.setTitle(EntityType.ORGANIZATION, [org.id], org.name));
    }

    /**
     * Get number of allowed complaints in this course.
     * @param courseId
     * @param teamMode If true, the number of allowed complaints for the user's team is returned
     */
    getNumberOfAllowedComplaintsInCourse(courseId: number, teamMode = false): Observable<number> {
        // Note: 0 is the default value in case the server returns something that does not make sense
        return this.http.get<number>(`${this.resourceUrl}/${courseId}/allowed-complaints?teamMode=${teamMode}`) ?? 0;
    }

    getSemesterCollapseStateFromStorage(storageId: string): boolean {
        const storedCollapseState = this.localStorageService.retrieve<boolean>('semester.collapseState.' + storageId);
        return storedCollapseState ?? false;
    }

    setSemesterCollapseState(storageId: string, isCollapsed: boolean) {
        this.localStorageService.store<boolean>('semester.collapseState.' + storageId, isCollapsed);
    }
}
