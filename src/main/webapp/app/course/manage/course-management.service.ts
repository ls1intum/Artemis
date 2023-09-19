import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { filter, map, tap } from 'rxjs/operators';
import { Course, CourseGroup } from 'app/entities/course.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { User, UserPublicInfoDTO } from 'app/core/user/user.model';
import { LectureService } from 'app/lecture/lecture.service';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { createRequestOption } from 'app/shared/util/request.util';
import { Submission, reconnectSubmissions } from 'app/entities/submission.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { StudentDTO } from 'app/entities/student-dto.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { convertDateFromClient } from 'app/utils/date.utils';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { CourseForDashboardDTO } from 'app/course/manage/course-for-dashboard-dto';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ExerciseType, ScoresPerExerciseType } from 'app/entities/exercise.model';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

export type RoleGroup = 'tutors' | 'students' | 'instructors' | 'editors';

@Injectable({ providedIn: 'root' })
export class CourseManagementService {
    private resourceUrl = 'api/courses';

    private coursesForNotifications: BehaviorSubject<Course[] | undefined> = new BehaviorSubject<Course[] | undefined>(undefined);

    private fetchingCoursesForNotifications = false;

    constructor(
        private http: HttpClient,
        private courseStorageService: CourseStorageService,
        private lectureService: LectureService,
        private accountService: AccountService,
        private entityTitleService: EntityTitleService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private tutorialGroupsService: TutorialGroupsService,
        private scoresStorageService: ScoresStorageService,
    ) {}

    /**
     * updates a course using a PUT request
     * @param courseId - the id of the course to be updated
     * @param courseUpdate - the updates to the course
     * @param courseImage - the course icon file
     */
    update(courseId: number, courseUpdate: Course, courseImage?: Blob): Observable<EntityResponseType> {
        const copy = CourseManagementService.convertCourseDatesFromClient(courseUpdate);
        const formData = new FormData();
        formData.append('course', objectToJsonBlob(copy));
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
        return this.http.put<OnlineCourseConfiguration>(`${this.resourceUrl}/${courseId}/onlineCourseConfiguration`, onlineCourseConfiguration, { observe: 'response' });
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
     */
    getStatisticsData(courseId: number, periodIndex: number): Observable<number[]> {
        const params = new HttpParams().set('periodIndex', '' + periodIndex);
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
     * finds a course with the given id and eagerly loaded organizations
     * @param courseId the id of the course to be found
     */
    findWithOrganizations(courseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Course>(`${this.resourceUrl}/${courseId}/with-organizations`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processCourseEntityResponseType(res)));
    }

    // TODO: separate course overview and course management REST API calls in a better way
    /**
     * finds all courses using a GET request
     */
    findAllForDashboard(): Observable<EntityArrayResponseType> {
        this.fetchingCoursesForNotifications = true;
        return this.http.get<CourseForDashboardDTO[]>(`${this.resourceUrl}/for-dashboard`, { observe: 'response' }).pipe(
            map((res: HttpResponse<CourseForDashboardDTO[]>) => {
                if (res.body) {
                    const courses: Course[] = [];
                    res.body.forEach((courseForDashboardDTO) => {
                        courses.push(courseForDashboardDTO.course);
                        this.saveScoresInStorage(courseForDashboardDTO);
                    });
                    // Replace the CourseForDashboardDTOs in the response body with the normal courses to enable further processing.
                    return res.clone({ body: courses });
                }
                return res;
            }),
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res)),
            tap((res: EntityArrayResponseType) => this.courseStorageService.setCourses(res.body !== null ? res.body : undefined)),
        );
    }

    /**
     * Finds one course using a GET request.
     * If the course was already loaded it should be retrieved using {@link CourseStorageService#getCourse} or {@link CourseStorageService#subscribeToCourseUpdates}
     * @param courseId the course to fetch
     * @param userRefresh whether this is a user-initiated refresh (default: false)
     */
    findOneForDashboard(courseId: number, userRefresh = false): Observable<EntityResponseType> {
        let params = new HttpParams();
        if (userRefresh) {
            params = params.set('refresh', String(true));
        }
        return this.http.get<CourseForDashboardDTO>(`${this.resourceUrl}/${courseId}/for-dashboard`, { params, observe: 'response' }).pipe(
            map((res: HttpResponse<CourseForDashboardDTO>) => {
                if (res.body) {
                    const courseForDashboardDTO: CourseForDashboardDTO = res.body;
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

    saveScoresInStorage(courseForDashboardDTO: CourseForDashboardDTO) {
        // Save the total scores in the scores-storage.service.
        this.scoresStorageService.setStoredTotalScores(courseForDashboardDTO.course.id!, courseForDashboardDTO.totalScores);

        const scoresPerExerciseType: ScoresPerExerciseType = new Map();
        scoresPerExerciseType.set(ExerciseType.PROGRAMMING, courseForDashboardDTO.programmingScores);
        scoresPerExerciseType.set(ExerciseType.MODELING, courseForDashboardDTO.modelingScores);
        scoresPerExerciseType.set(ExerciseType.QUIZ, courseForDashboardDTO.quizScores);
        scoresPerExerciseType.set(ExerciseType.TEXT, courseForDashboardDTO.textScores);
        scoresPerExerciseType.set(ExerciseType.FILE_UPLOAD, courseForDashboardDTO.fileUploadScores);
        this.scoresStorageService.setStoredScoresPerExerciseType(courseForDashboardDTO.course.id!, scoresPerExerciseType);

        // Save the participation results in the scores-storage.service.
        this.scoresStorageService.setStoredParticipationResults(courseForDashboardDTO.participationResults);
    }

    /**
     * finds all participants of the course corresponding to the given unique identifier
     * @param courseId - the id of the course
     */
    findAllParticipationsWithResults(courseId: number): Observable<StudentParticipation[]> {
        return this.http.get<StudentParticipation[]>(`${this.resourceUrl}/${courseId}/participations`);
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
    registerForCourse(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.post<string[]>(`${this.resourceUrl}/${courseId}/enroll`, null, { observe: 'response' }).pipe(
            map((res: HttpResponse<string[]>) => {
                if (res.body != undefined) {
                    this.accountService.syncGroups(res.body);
                }
                return res;
            }),
        );
    }

    /**
     * unenroll from course with the provided unique identifier using a POST request
     * NB: the body is null, because the server can identify the user anyway
     * @param courseId - the id of the course
     */
    unenrollFromCourse(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.post<string[]>(`${this.resourceUrl}/${courseId}/unenroll`, null, { observe: 'response' }).pipe(
            map((res: HttpResponse<string[]>) => {
                if (res.body != undefined) {
                    this.accountService.syncGroups(res.body);
                }
                return res;
            }),
        );
    }

    /**
     * finds all courses using a GET request
     * @param req
     */
    getAll(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        this.fetchingCoursesForNotifications = true;
        return this.http.get<Course[]>(this.resourceUrl, { params: options, observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res)),
        );
    }

    /**
     * finds all courses with quiz exercises using a GET request
     */
    getAllCoursesWithQuizExercises(): Observable<EntityArrayResponseType> {
        this.fetchingCoursesForNotifications = true;
        return this.http.get<Course[]>(this.resourceUrl + '/courses-with-quiz', { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res)),
        );
    }

    /**
     * finds all courses together with user stats using a GET request
     * @param req
     */
    getWithUserStats(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        this.fetchingCoursesForNotifications = true;
        return this.http.get<Course[]>(`${this.resourceUrl}/with-user-stats`, { params: options, observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res)),
        );
    }

    /**
     * finds all courses for the overview using a GET request
     * @param req a dictionary which is sent as request option along the REST call
     */
    getCourseOverview(req?: any): Observable<HttpResponse<Course[]>> {
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
     * returns the exercise details of the courses for the courses' management dashboard
     * @param onlyActive - if true, only active courses will be considered in the result
     */
    getExercisesForManagementOverview(onlyActive: boolean): Observable<HttpResponse<Course[]>> {
        let httpParams = new HttpParams();
        httpParams = httpParams.append('onlyActive', onlyActive.toString());
        return this.http
            .get<Course[]>(`${this.resourceUrl}/exercises-for-management-overview`, { params: httpParams, observe: 'response' })
            .pipe(map((res: HttpResponse<Course[]>) => this.processCourseEntityArrayResponseType(res)));
    }

    /**
     * returns the stats of the courses for the courses' management dashboard
     * @param onlyActive - if true, only active courses will be considered in the result
     */
    getStatsForManagementOverview(onlyActive: boolean): Observable<HttpResponse<CourseManagementOverviewStatisticsDto[]>> {
        let httpParams = new HttpParams();
        httpParams = httpParams.append('onlyActive', onlyActive.toString());
        return this.http.get<CourseManagementOverviewStatisticsDto[]>(`${this.resourceUrl}/stats-for-management-overview`, { params: httpParams, observe: 'response' });
    }

    /**
     * returns all the categories of the course corresponding to the given unique identifier
     * @param courseId - the id of the course
     */
    findAllCategoriesOfCourse(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(`${this.resourceUrl}/${courseId}/categories`, { observe: 'response' });
    }

    /**
     * returns all the users in the given group of the course corresponding to the given unique identifier
     * @param courseId - the id of the course
     * @param courseGroup - the course group we want to get users from
     */
    getAllUsersInCourseGroup(courseId: number, courseGroup: CourseGroup): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`${this.resourceUrl}/${courseId}/${courseGroup}`, { observe: 'response' });
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
    archiveCourse(courseId: number): Observable<HttpResponse<any>> {
        return this.http.put(`${this.resourceUrl}/${courseId}/archive`, {}, { observe: 'response' });
    }

    cleanupCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}/cleanup`, { observe: 'response' });
    }

    /**
     * Find all locked submissions of a given course for user
     * @param {number} courseId - The id of the course to be searched for
     */
    findAllLockedSubmissionsOfCourse(courseId: number): Observable<HttpResponse<Submission[]>> {
        return this.http.get<Submission[]>(`${this.resourceUrl}/${courseId}/lockedSubmissions`, { observe: 'response' }).pipe(
            filter((res) => !!res.body),
            tap((res) => reconnectSubmissions(res.body!)),
        );
    }

    /**
     * adds a user to the given courseGroup of the course corresponding to the given unique identifier using a POST request
     * @param courseId - the id of the course
     * @param courseGroup - the course group we want to add a user to
     * @param login - login of the user to be added
     */
    addUserToCourseGroup(courseId: number, courseGroup: CourseGroup, login: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${courseId}/${courseGroup}/${login}`, {}, { observe: 'response' });
    }

    /**
     * Add users to the registered users for a course.
     * @param courseId to which the users shall be added.
     * @param studentDtos Student DTOs of users to add to the course.
     * @param courseGroup the course group into which the user should be added
     * @return studentDtos of users that were not found in the system.
     */
    addUsersToGroupInCourse(courseId: number, studentDtos: StudentDTO[], courseGroup: string): Observable<HttpResponse<StudentDTO[]>> {
        return this.http.post<StudentDTO[]>(`${this.resourceUrl}/${courseId}/${courseGroup}`, studentDtos, { observe: 'response' });
    }

    /**
     * removes a user from the given group of the course corresponding to the given unique identifier using a DELETE request
     * @param courseId - the id of the course
     * @param courseGroup - the course group
     * @param login - login of the user to be removed
     */
    removeUserFromCourseGroup(courseId: number, courseGroup: CourseGroup, login: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}/${courseGroup}/${login}`, { observe: 'response' });
    }

    /**
     * Gets the cached courses. If there are none, the courses for the current user will be fetched.
     * @returns {BehaviorSubject<Course[] | undefined>}
     */
    getCoursesForNotifications(): BehaviorSubject<Course[] | undefined> {
        // The timeout is set to ensure that the request for retrieving courses
        // here is only made if there was no similar request made before.
        setTimeout(() => {
            // Retrieve courses if no courses were fetched before and are not queried at the moment.
            if (!this.fetchingCoursesForNotifications && !this.coursesForNotifications.getValue()) {
                this.findAllForNotifications().subscribe({
                    next: (res: HttpResponse<Course[]>) => {
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

    private setCoursesForNotifications(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            this.coursesForNotifications.next(res.body);
            this.fetchingCoursesForNotifications = false;
        }
        return res;
    }

    static convertCourseDatesFromClient(course: Course): Course {
        // copy of the object
        return Object.assign({}, course, {
            startDate: convertDateFromClient(course.startDate),
            endDate: convertDateFromClient(course.endDate),
            enrollmentStartDate: convertDateFromClient(course.enrollmentStartDate),
            enrollmentEndDate: convertDateFromClient(course.enrollmentEndDate),
            unenrollmentEndDate: convertDateFromClient(course.unenrollmentEndDate),
        });
    }

    private convertTutorialGroupDatesFromServer(courseRes: EntityResponseType): EntityResponseType {
        if (courseRes.body?.tutorialGroups) {
            courseRes.body.tutorialGroups = this.tutorialGroupsService.convertTutorialGroupArrayDatesFromServer(courseRes.body.tutorialGroups);
        }
        return courseRes;
    }

    private convertTutorialGroupsDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                if (course.tutorialGroups) {
                    course.tutorialGroups = this.tutorialGroupsService.convertTutorialGroupArrayDatesFromServer(course.tutorialGroups);
                }
            });
        }
        return res;
    }

    private convertTutorialGroupConfigurationDateFromServer(courseRes: EntityResponseType): EntityResponseType {
        if (courseRes.body?.tutorialGroupsConfiguration) {
            courseRes.body.tutorialGroupsConfiguration = this.tutorialGroupsConfigurationService.convertTutorialGroupsConfigurationDatesFromServer(
                courseRes.body.tutorialGroupsConfiguration,
            );
        }
        return courseRes;
    }

    private convertTutorialGroupConfigurationsDateFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((course: Course) => {
                if (course.tutorialGroupsConfiguration) {
                    course.tutorialGroupsConfiguration = this.tutorialGroupsConfigurationService.convertTutorialGroupsConfigurationDatesFromServer(
                        course.tutorialGroupsConfiguration,
                    );
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

    private findAllForNotifications(): Observable<EntityArrayResponseType> {
        this.fetchingCoursesForNotifications = true;
        return this.http.get<Course[]>(`${this.resourceUrl}/for-notifications`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.processCourseEntityArrayResponseType(res)),
            map((res: EntityArrayResponseType) => this.setCoursesForNotifications(res)),
        );
    }

    sendCourseTitleAndExerciseTitlesToTitleService(course: Course | null | undefined) {
        this.entityTitleService.setTitle(EntityType.COURSE, [course?.id], course?.title);

        course?.exercises?.forEach((exercise) => {
            this.entityTitleService.setTitle(EntityType.EXERCISE, [exercise.id], exercise.title);
        });
        course?.lectures?.forEach((lecture) => this.entityTitleService.setTitle(EntityType.LECTURE, [lecture.id], lecture.title));
        course?.exams?.forEach((exam) => this.entityTitleService.setTitle(EntityType.EXAM, [exam.id], exam.title));
        course?.organizations?.forEach((org) => this.entityTitleService.setTitle(EntityType.ORGANIZATION, [org.id], org.name));
    }
}
