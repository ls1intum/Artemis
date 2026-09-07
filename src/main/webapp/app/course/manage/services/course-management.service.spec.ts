import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { CourseManagementDetailViewDto } from 'app/course/shared/entities/course-management-detail-view-dto.model';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { Organization } from 'app/admin/organization-management/organization.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { take } from 'rxjs/operators';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { CourseForDashboardDTO, ParticipationResultDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { OnlineCourseDtoModel } from 'app/lti/shared/entities/online-course-dto.model';
import { CoursesForDashboardDTO } from 'app/course/shared/entities/courses-for-dashboard-dto';
import { provideHttpClient } from '@angular/common/http';
import { createSampleCourse } from 'test/helpers/sample/course-sample-data';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { BehaviorSubject, distinctUntilChanged } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { EntityTitleService } from 'app/core/navbar/entity-title.service';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';

const courseDateFields = ['startDate', 'endDate', 'enrollmentStartDate', 'enrollmentEndDate', 'unenrollmentEndDate'] as const satisfies readonly (keyof Course)[];
type CourseDateField = (typeof courseDateFields)[number];

describe('Course Management Service', () => {
    let courseManagementService: CourseManagementService;
    let accountService: AccountService;
    let lectureService: LectureService;
    let httpMock: HttpTestingController;
    let courseStorageService: CourseStorageService;
    let scoresStorageService: ScoresStorageService;
    let localStorageService: LocalStorageService;
    let courseNotificationService: CourseNotificationService;
    let entityTitleService: EntityTitleService;

    let isAtLeastTutorInCourseSpy: ReturnType<typeof vi.spyOn>;
    let isAtLeastEditorInCourseSpy: ReturnType<typeof vi.spyOn>;
    let isAtLeastInstructorInCourseSpy: ReturnType<typeof vi.spyOn>;
    let convertExercisesDateFromServerSpy: ReturnType<typeof vi.spyOn>;
    let convertDatesForLecturesFromServerSpy: ReturnType<typeof vi.spyOn>;

    const resourceUrl = 'api/course/courses';

    let course: Course;
    let courseForDashboard: CourseForDashboardDTO;
    let coursesForDashboard: CoursesForDashboardDTO;
    let courseScores: CourseScores;
    let scoresPerExerciseType: ScoresPerExerciseType;
    let participationResult: ParticipationResultDTO;
    let onlineCourseConfiguration: OnlineCourseConfiguration;
    let exercises: Exercise[];
    let returnedFromService: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        courseManagementService = TestBed.inject(CourseManagementService);
        httpMock = TestBed.inject(HttpTestingController);
        accountService = TestBed.inject(AccountService);
        lectureService = TestBed.inject(LectureService);
        courseStorageService = TestBed.inject(CourseStorageService);
        scoresStorageService = TestBed.inject(ScoresStorageService);
        localStorageService = TestBed.inject(LocalStorageService);
        courseNotificationService = TestBed.inject(CourseNotificationService);
        entityTitleService = TestBed.inject(EntityTitleService);

        isAtLeastTutorInCourseSpy = vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(false);
        isAtLeastEditorInCourseSpy = vi.spyOn(accountService, 'isAtLeastEditorInCourse').mockReturnValue(false);
        isAtLeastInstructorInCourseSpy = vi.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(false);
        convertDatesForLecturesFromServerSpy = vi.spyOn(lectureService, 'convertLectureArrayDatesFromServer');
        ({ course, exercises } = createSampleCourse());

        courseForDashboard = new CourseForDashboardDTO();
        courseForDashboard.course = course;
        courseScores = new CourseScores(0, 0, 0, { absoluteScore: 0, absoluteScoreTotal: 0, relativeScore: 0, currentRelativeScore: 0, presentationScore: 0 });
        courseForDashboard.totalScores = courseScores;
        courseForDashboard.programmingScores = courseScores;
        courseForDashboard.modelingScores = courseScores;
        courseForDashboard.quizScores = courseScores;
        courseForDashboard.textScores = courseScores;
        courseForDashboard.fileUploadScores = courseScores;
        participationResult = new ParticipationResultDTO();
        participationResult.participationId = 432;
        courseForDashboard.participationResults = [participationResult];

        coursesForDashboard = new CoursesForDashboardDTO();
        coursesForDashboard.courses = [courseForDashboard];

        scoresPerExerciseType = new Map<ExerciseType, CourseScores>();
        scoresPerExerciseType.set(ExerciseType.PROGRAMMING, courseScores);
        scoresPerExerciseType.set(ExerciseType.MODELING, courseScores);
        scoresPerExerciseType.set(ExerciseType.QUIZ, courseScores);
        scoresPerExerciseType.set(ExerciseType.TEXT, courseScores);
        scoresPerExerciseType.set(ExerciseType.FILE_UPLOAD, courseScores);

        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.id = 234;
        returnedFromService = { ...course } as Course;
        convertExercisesDateFromServerSpy = vi.spyOn(ExerciseService, 'convertExercisesDateFromServer').mockReturnValue(exercises);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    const expectDateConversionToBeCalled = (courseForConversion: Course) => {
        expect(convertExercisesDateFromServerSpy).toHaveBeenCalledWith(courseForConversion.exercises);
        expect(convertDatesForLecturesFromServerSpy).toHaveBeenCalledWith(courseForConversion.lectures);
    };

    const expectAccessRightsToBeCalled = (tutorTimes: number, editorTimes: number, instructorTimes: number) => {
        expect(isAtLeastTutorInCourseSpy).toHaveBeenCalledTimes(tutorTimes);
        expect(isAtLeastEditorInCourseSpy).toHaveBeenCalledTimes(editorTimes);
        expect(isAtLeastInstructorInCourseSpy).toHaveBeenCalledTimes(instructorTimes);
    };

    const requestAndExpectDateConversion = (method: string, url: string, flushedObject: any = returnedFromService, courseToCheck: Course, checkAccessRights?: boolean) => {
        const req = httpMock.expectOne({ method, url });
        req.flush(flushedObject);
        expectDateConversionToBeCalled(courseToCheck);
        if (checkAccessRights) {
            expectAccessRightsToBeCalled(3, 3, 3);
        }
    };

    it('should update course', () => {
        const courseImage = new Blob();
        courseManagementService
            .update(1, { ...course }, courseImage)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));

        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/1` });
        req.flush(returnedFromService);
    });

    it('should update online course configuration', () => {
        courseManagementService
            .updateOnlineCourseConfiguration(1, onlineCourseConfiguration)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));

        const req = httpMock.expectOne({ method: 'PUT', url: `api/lti/courses/1/online-course-configuration` });
        req.flush(returnedFromService);
    });

    it('should fetch online courses for given registration ID', () => {
        const mockClientId = 'client-123';
        const mockResponse: OnlineCourseDtoModel[] = [
            { id: 1, title: 'Course A', shortName: 'cA', registrationId: '1234' },
            { id: 2, title: 'Course B', shortName: 'cB', registrationId: '1234' },
            { id: 3, title: 'Course C', shortName: 'cC', registrationId: '3214' },
        ];

        courseManagementService.findAllOnlineCoursesWithRegistrationId(mockClientId).subscribe((courses) => {
            expect(courses).toEqual(mockResponse);
        });

        const req = httpMock.expectOne(`api/lti/courses/for-lti-dashboard?clientId=${mockClientId}`);
        expect(req.request.method).toBe('GET');
        req.flush(mockResponse);
    });

    it('should find the course', () => {
        courseManagementService
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}`, returnedFromService, course);
    });

    it('should convert all course date fields from server ISO strings to dayjs on find', () => {
        const isoDates = {
            startDate: '2026-10-14T14:00:00Z',
            endDate: '2027-02-01T00:00:00Z',
            enrollmentStartDate: '2026-10-14T14:00:00Z',
            enrollmentEndDate: '2026-11-01T13:00:00Z',
            unenrollmentEndDate: '2026-12-01T18:00:00Z',
        } satisfies Record<CourseDateField, string>;
        const serverCourse = deepClone(course);
        Object.assign(serverCourse, isoDates);

        let body: Course | undefined;
        courseManagementService
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => (body = res.body!));
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}` }).flush(serverCourse as any);

        expect(body).toBeDefined();
        const areDayjs = Object.fromEntries(courseDateFields.map((field) => [field, dayjs.isDayjs(body![field])]));
        expect(areDayjs).toEqual({
            startDate: true,
            endDate: true,
            enrollmentStartDate: true,
            enrollmentEndDate: true,
            unenrollmentEndDate: true,
        });
        // Hardcoded expected instants so dayjs is checked against an independent oracle, not against itself.
        const actualIso = Object.fromEntries(courseDateFields.map((field) => [field, (body![field] as dayjs.Dayjs).toISOString()]));
        expect(actualIso).toEqual({
            startDate: '2026-10-14T14:00:00.000Z',
            endDate: '2027-02-01T00:00:00.000Z',
            enrollmentStartDate: '2026-10-14T14:00:00.000Z',
            enrollmentEndDate: '2026-11-01T13:00:00.000Z',
            unenrollmentEndDate: '2026-12-01T18:00:00.000Z',
        });
    });

    it('should convert null course date fields to undefined on find', () => {
        // setCourseDates cannot distinguish null, undefined or absent (truthy check), so null for all five covers the same branch.
        const nullDates = {
            startDate: null,
            endDate: null,
            enrollmentStartDate: null,
            enrollmentEndDate: null,
            unenrollmentEndDate: null,
        } satisfies Record<CourseDateField, null>;
        const serverCourse = deepClone(course);
        Object.assign(serverCourse, nullDates);

        let body: Course | undefined;
        courseManagementService
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => (body = res.body!));
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}` }).flush(serverCourse as any);

        expect(body).toBeDefined();
        expect(body!.startDate).toBeUndefined();
        expect(body!.endDate).toBeUndefined();
        expect(body!.enrollmentStartDate).toBeUndefined();
        expect(body!.enrollmentEndDate).toBeUndefined();
        expect(body!.unenrollmentEndDate).toBeUndefined();
    });

    it('should set accessRights with by using the AccountService', () => {
        courseManagementService
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}`, returnedFromService, course, true);
    });

    it('should find course with exercises', () => {
        courseManagementService
            .findWithExercises(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises`, returnedFromService, course);
    });

    it('should find course with organizations', () => {
        course.organizations = [new Organization()];
        returnedFromService = { ...course };
        courseManagementService
            .findWithOrganizations(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-organizations`, returnedFromService, course);
    });

    it('should find all courses for dashboard', () => {
        const courseStorageServiceSpy = vi.spyOn(courseStorageService, 'setCourses');
        returnedFromService = coursesForDashboard;
        courseManagementService
            .findAllForDashboard()
            .pipe(take(1))
            .subscribe((res) => {
                expect(res.body!.courses[0].course).toEqual(course);
                expect(courseStorageServiceSpy).toHaveBeenCalledOnce();
            });
        requestAndExpectDateConversion('GET', `${resourceUrl}/for-dashboard`, returnedFromService, course);
    });

    it('should pass on an empty response body when fetching all courses for dashboard and there is no response body sent from the server', () => {
        courseManagementService.findAllForDashboard().subscribe((res) => expect(res.body).toBeNull());

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/for-dashboard` });
        req.flush(null);
    });

    // `findOneForDashboard` is deprecated for the web client but the endpoint stays for the iOS, Android and
    // VS Code clients, so these three tests are the only remaining coverage of it and have to keep calling it.
    it('should find one course for dashboard', () => {
        returnedFromService = { ...courseForDashboard };
        courseStorageService
            .subscribeToCourseUpdates(course.id!)
            .pipe(take(1))
            .subscribe((updatedCourse) => {
                expect(updatedCourse).toEqual(course);
            });
        courseManagementService
            // eslint-disable-next-line @typescript-eslint/no-deprecated -- see the note above this test
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-dashboard`, returnedFromService, course, true);
    });

    it('should pass on an empty response body when fetching one course for dashboard and there is no response body sent from the server', () => {
        // eslint-disable-next-line @typescript-eslint/no-deprecated -- see the note on the test above
        courseManagementService.findOneForDashboard(course.id!).subscribe((res) => expect(res.body).toBeNull());

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/for-dashboard` });
        req.flush(null);
    });

    it('should set the totalScores, the scoresPerExerciseType, and the participantScores in the scoresStorageService', () => {
        const setStoredTotalScoresSpy = vi.spyOn(scoresStorageService, 'setStoredTotalScores');
        const setStoredScoresPerExerciseTypeSpy = vi.spyOn(scoresStorageService, 'setStoredScoresPerExerciseType');
        const setParticipationResultsSpy = vi.spyOn(scoresStorageService, 'setStoredParticipationResults');
        const setAchievedGroupPointsSpy = vi.spyOn(scoresStorageService, 'setStoredAchievedPointsPerVariantGroup');
        courseManagementService
            // eslint-disable-next-line @typescript-eslint/no-deprecated -- see the note two tests above
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe(() => {
                expect(setStoredTotalScoresSpy).toHaveBeenCalledWith(course.id!, courseScores);
                expect(setStoredScoresPerExerciseTypeSpy).toHaveBeenCalledWith(course.id!, scoresPerExerciseType);
                expect(setParticipationResultsSpy).toHaveBeenCalledWith(courseForDashboard.participationResults);
                expect(setAchievedGroupPointsSpy).toHaveBeenCalledWith(course.id!, courseForDashboard.achievedPointsPerVariantGroup);
            });
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/for-dashboard` });
        req.flush(courseForDashboard);
    });

    it('should find grade scores for the course', () => {
        const gradeInformation = {
            gradeScores: [],
            students: [],
        };
        returnedFromService = gradeInformation;
        courseManagementService
            .findGradeScores(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(gradeInformation));
        const req = httpMock.expectOne({ method: 'GET', url: `api/assessment/courses/${course.id}/grade-scores` });
        req.flush(returnedFromService);
    });

    it('should find results for the course', () => {
        courseManagementService.findAllResultsOfCourseForExerciseAndCurrentUser(course.id!).subscribe((res) => expect(res).toEqual(course));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/results` });
        req.flush(returnedFromService);
    });

    it('should find all courses to register', () => {
        returnedFromService = [{ ...course }];
        courseManagementService
            .findAllForRegistration()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/for-enrollment`, returnedFromService, course);
    });

    it('should find course with interesting exercises', () => {
        courseManagementService
            .getCourseWithInterestingExercisesForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-assessment-dashboard`, returnedFromService, course);
    });

    it('should get stats of course', () => {
        const stats = new StatsForDashboard();
        returnedFromService = { ...stats };
        courseManagementService
            .getStatsForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/stats-for-assessment-dashboard` });
        req.flush(returnedFromService);
    });

    it('should getStatisticsData', () => {
        const periodIndex = 0;
        const periodSize = 5;
        const statsData = [1, 2, 3, 4, 5];
        courseManagementService
            .getStatisticsData(course.id!, periodIndex, periodSize)
            .pipe(take(1))
            .subscribe((stats) => expect(stats).toHaveLength(periodSize));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/statistics?periodIndex=${periodIndex}&periodSize=${periodSize}` });
        req.flush(statsData);
    });

    it('should register for the course', () => {
        courseManagementService.registerForCourse(course.id!).pipe(take(1)).subscribe();
        httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/enroll` }).flush(null);
    });

    it('should unenroll from the course', () => {
        courseManagementService.unenrollFromCourse(course.id!).pipe(take(1)).subscribe();
        httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/unenroll` }).flush(null);
    });

    it('should get all courses with quiz exercises', () => {
        returnedFromService = [{ ...course }];
        courseManagementService
            .getAllCoursesWithQuizExercises()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/courses-with-quiz`, returnedFromService, course, true);
    });

    it('should get all courses for overview', () => {
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        courseManagementService
            .getCourseOverview(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/course-management-overview?testParam=testParamValue` });
        req.flush(returnedFromService);
        expectAccessRightsToBeCalled(1, 1, 1);
    });

    it('should find all categories of course', () => {
        const categories = ['category1', 'category2'];
        returnedFromService = [...categories];
        courseManagementService
            .findAllCategoriesOfCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(categories));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/categories` });
        req.flush(returnedFromService);
    });

    it('should find all users of course group', () => {
        const users = [new User(1, 'user1'), new User(2, 'user2')];
        returnedFromService = [...users];
        const courseRoleSlug = CourseRoleSlug.STUDENTS;
        courseManagementService
            .getAllUsersInCourseRole(course.id!, courseRoleSlug)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(users));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/${courseRoleSlug}` });
        req.flush(returnedFromService);
    });

    it('should download course archive', () => {
        const windowSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        courseManagementService.downloadCourseArchive(1);
        expect(windowSpy).toHaveBeenCalledWith(`${resourceUrl}/1/download-archive`, '_blank');
    });

    it('should archive the course', () => {
        courseManagementService.archiveCourse(course.id!).subscribe((res) => expect(res.body).toEqual(course));
        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/${course.id}/archive` });
        req.flush(returnedFromService);
    });

    it('should clean up the course', () => {
        courseManagementService.cleanupCourse(course.id!).subscribe((res) => expect(res.body).toEqual(course));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/cleanup` });
        req.flush(returnedFromService);
    });

    it('should find all locked submissions of course', () => {
        const submission = new ModelingSubmission();
        const submissions = [submission];
        returnedFromService = [...submissions];
        courseManagementService.findAllLockedSubmissionsOfCourse(course.id!).subscribe((res) => expect(res.body).toEqual(submissions));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/locked-submissions` });
        req.flush(returnedFromService);
    });

    it('should add user to course group', () => {
        const user = new User(1, 'name');
        const courseRoleSlug = CourseRoleSlug.STUDENTS;
        courseManagementService
            .addUserToCourseRole(course.id!, courseRoleSlug, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/${courseRoleSlug}/${user.login}` });
        req.flush({});
    });

    it('should remove user from course group', () => {
        const user = new User(1, 'name');
        const courseRoleSlug = CourseRoleSlug.STUDENTS;
        courseManagementService
            .removeUserFromCourseRole(course.id!, courseRoleSlug, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/${courseRoleSlug}/${user.login}` });
        req.flush({});
    });

    it('should return lifetime overview data', () => {
        const stats = [34, 23, 45, 67, 89, 201, 67, 890, 1359];
        courseManagementService
            .getStatisticsForLifetimeOverview(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/statistics-lifetime-overview` });
        req.flush(stats);
    });

    it('should search other users within course', () => {
        const users = [new User(1, 'user1')];
        returnedFromService = [...users];
        courseManagementService
            .searchOtherUsersInCourse(course.id!, 'user1')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(users));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/search-other-users?nameOfUser=user1` });
        req.flush(returnedFromService);
    });

    it('getNumberOfAllowedComplaintsInCourse', () => {
        const courseId = 42;
        const teamMode = true;
        const expectedCount = 69;

        courseManagementService.getNumberOfAllowedComplaintsInCourse(courseId, teamMode).subscribe((received) => {
            expect(received).toBe(expectedCount);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`${resourceUrl}/${courseId}/allowed-complaints?teamMode=true`);

        res.flush(expectedCount);
    });

    it('should fetch a non-empty management detail response and filter an empty one', () => {
        const detail = { numberOfStudents: 12 } as unknown as CourseManagementDetailViewDto;
        const next = vi.fn();
        courseManagementService.getCourseStatisticsForDetailView(7).subscribe(next);
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/7/management-detail` }).flush(detail);
        expect(next).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ body: detail }));

        courseManagementService.getCourseStatisticsForDetailView(8).subscribe(next);
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/8/management-detail` }).flush(null);
        expect(next).toHaveBeenCalledTimes(1);
    });

    it('should fetch a course with exercises, lectures, and competencies through its dedicated endpoint', () => {
        courseManagementService.findWithExercisesAndLecturesAndCompetencies(course.id!).subscribe((response) => expect(response.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises-lectures-competencies`, returnedFromService, course);
    });

    it('should fetch the minimal course list for dropdowns', () => {
        courseManagementService.findAllForDropdown().subscribe((response) => expect(response.body).toEqual([course]));
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/for-dropdown` }).flush([course]);
    });

    it('should fetch the limited course representation used by registration fallback', () => {
        courseManagementService.findOneForRegistration(course.id!).subscribe((response) => expect(response.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-enrollment`, returnedFromService, course);
    });

    it('should fetch the course archive summaries without requesting full courses', () => {
        const archives = [{ id: 7, title: 'Archived course' }];
        courseManagementService.getCoursesForArchive().subscribe((response) => expect(response.body).toEqual(archives));
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/for-archive` }).flush(archives);
    });

    describe('Semester collapse state storage', () => {
        it('should return false if no collapse state is stored', () => {
            const collapseState = courseManagementService.getSemesterCollapseStateFromStorage('2024');
            expect(collapseState).toBe(false);
        });

        it('should store the collapse state via service method and retrieve it correctly', () => {
            const storageId = '2026';
            courseManagementService.setSemesterCollapseState(storageId, false);

            const storedValue = localStorageService.retrieve<boolean>(`semester.collapseState.${storageId}`);
            expect(storedValue).toBe(false);

            const retrieved = courseManagementService.getSemesterCollapseStateFromStorage(storageId);
            expect(retrieved).toBe(false);
        });
    });

    it('should not drop exercises a per-tab loader already published when the lean course lands afterwards', () => {
        // The course record and the exercise list are separate requests that can finish in either order; the lean
        // course must not wipe exercises that already arrived, or the exercises tab renders empty.
        courseStorageService.updateCourse({ id: 7, exercises: [{ id: 99 }] } as Course);

        const notificationSpy = vi.spyOn(courseNotificationService, 'updateNotificationCountMap');
        let responseCourse: Course | null | undefined;
        courseManagementService.findCourseForOverview(7).subscribe((response) => (responseCourse = response.body));
        // The endpoint returns flat scalars now, not a nested course object
        httpMock.expectOne({ method: 'GET', url: 'api/course/courses/7/for-overview' }).flush({ id: 7, title: 'Course', courseNotificationCount: 4 });

        expect(responseCourse).toMatchObject({ id: 7, title: 'Course' });
        expect(notificationSpy).toHaveBeenCalledExactlyOnceWith(7, 4);
        expect(courseStorageService.getCourse(7)?.exercises).toHaveLength(1);
        expect(courseStorageService.getCourse(7)?.exercises?.[0].id).toBe(99);
        expect(courseStorageService.getCourse(7)?.title).toBe('Course');
    });

    it('should hydrate the course-level Athena flags from the lean course-overview response', () => {
        // Student-facing feedback-request controls gate on these flags, so the lean projection must carry them
        // even though it stays lean for everything else Athena-related.
        let responseCourse: Course | null | undefined;
        courseManagementService.findCourseForOverview(7).subscribe((response) => (responseCourse = response.body));
        httpMock
            .expectOne({ method: 'GET', url: 'api/course/courses/7/for-overview' })
            .flush({ id: 7, title: 'Course', athenaGradingFeedbackEnabled: true, athenaFormativeFeedbackEnabled: true, courseNotificationCount: 0 });

        expect(responseCourse).toMatchObject({ athenaGradingFeedbackEnabled: true, athenaFormativeFeedbackEnabled: true });
    });

    it('should drop a stored course when the lean-course response is empty', () => {
        const notificationSpy = vi.spyOn(courseNotificationService, 'updateNotificationCountMap');
        // Seeded so the removal is observable: an empty response must not leave the previous course readable
        courseStorageService.updateCourse({ id: 7, title: 'Course' } as Course);

        courseManagementService.findCourseForOverview(7).subscribe((response) => expect(response.body).toBeNull());
        httpMock.expectOne({ method: 'GET', url: 'api/course/courses/7/for-overview' }).flush(null);

        expect(notificationSpy).not.toHaveBeenCalled();
        expect(courseStorageService.getCourse(7)).toBeUndefined();
    });

    it('should convert and publish the exercise overview DTO and all score dimensions', () => {
        const convertedExercise = { id: 42, title: 'Converted exercise', type: ExerciseType.TEXT } as Exercise;
        const rawExercises = [{ id: 42, title: 'Raw exercise', type: ExerciseType.TEXT } as Exercise];
        const overview = {
            exercises: rawExercises,
            totalScores: courseScores,
            programmingScores: courseScores,
            modelingScores: courseScores,
            quizScores: courseScores,
            textScores: courseScores,
            fileUploadScores: courseScores,
            participationResults: [participationResult],
            achievedPointsPerVariantGroup: { 9: 7.5 },
        } as CourseExercisesForOverviewDTO;
        convertExercisesDateFromServerSpy.mockReturnValue([convertedExercise]);
        const parseCategoriesSpy = vi.spyOn(ExerciseService, 'parseExerciseCategories');
        const setTitleSpy = vi.spyOn(entityTitleService, 'setExerciseTitle');
        const setTotalSpy = vi.spyOn(scoresStorageService, 'setStoredTotalScores');
        const setTypesSpy = vi.spyOn(scoresStorageService, 'setStoredScoresPerExerciseType');
        const setResultsSpy = vi.spyOn(scoresStorageService, 'setStoredParticipationResults');
        const setGroupPointsSpy = vi.spyOn(scoresStorageService, 'setStoredAchievedPointsPerVariantGroup');

        courseManagementService.findCourseExercisesForOverview(7).subscribe((response) => {
            expect(response).toBe(overview);
            expect(response.exercises).toEqual([convertedExercise]);
        });
        httpMock.expectOne({ method: 'GET', url: 'api/course/courses/7/exercises-for-overview' }).flush(overview);

        expect(convertExercisesDateFromServerSpy).toHaveBeenCalledExactlyOnceWith(rawExercises);
        expect(parseCategoriesSpy).toHaveBeenCalledExactlyOnceWith(convertedExercise);
        expect(setTitleSpy).toHaveBeenCalledExactlyOnceWith(convertedExercise);
        expect(setTotalSpy).toHaveBeenCalledExactlyOnceWith(7, courseScores);
        expect(setTypesSpy).toHaveBeenCalledExactlyOnceWith(7, scoresPerExerciseType);
        expect(setResultsSpy).toHaveBeenCalledExactlyOnceWith([participationResult]);
        expect(setGroupPointsSpy).toHaveBeenCalledExactlyOnceWith(7, { 9: 7.5 });
    });

    it('should request the server-computed available tabs without deriving them on the client', () => {
        const tabs: CourseAvailableTabs = {
            lectures: true,
            exams: false,
            competencies: true,
            tutorialGroups: false,
            iris: true,
            faq: false,
            learningPaths: true,
            communication: true,
            training: false,
        };

        courseManagementService.getCourseAvailableTabs(7).subscribe((response) => expect(response).toEqual(tabs));
        const request = httpMock.expectOne({ method: 'GET', url: 'api/course/courses/7/available-tabs' });
        expect(request.request.params.keys()).toEqual([]);
        request.flush(tabs);
    });
});

describe('CourseManagementService - authentication state changes', () => {
    let authState: BehaviorSubject<User | undefined>;
    let scoped: CourseManagementService;
    let scopedHttpMock: HttpTestingController;

    beforeEach(() => {
        authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
        const customAccountService = new MockAccountService();
        customAccountService.userIdentity.set({ id: 99 } as User);
        customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useValue: customAccountService },
            ],
        });
        scoped = TestBed.inject(CourseManagementService);
        scopedHttpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        scopedHttpMock.verify();
    });

    it('should clear coursesForNotifications on logout', () => {
        scoped['coursesForNotifications'].next([{ id: 1 } as Course]);

        authState.next(undefined);

        expect(scoped['coursesForNotifications'].getValue()).toBeUndefined();
    });

    it('should clear coursesForNotifications when a different user logs in', () => {
        scoped['coursesForNotifications'].next([{ id: 1 } as Course]);

        authState.next({ id: 42 } as User);

        expect(scoped['coursesForNotifications'].getValue()).toBeUndefined();
    });

    it('should not clear coursesForNotifications when the same user re-emits', () => {
        scoped['coursesForNotifications'].next([{ id: 1 } as Course]);

        authState.next({ id: 99 } as User);

        expect(scoped['coursesForNotifications'].getValue()).toEqual([{ id: 1 } as Course]);
    });

    it('should ignore in-flight findAllForDashboard responses after logout', () => {
        const subscription = scoped.findAllForDashboard().subscribe();
        const inFlight = scopedHttpMock.expectOne(`api/course/courses/for-dashboard`);

        authState.next(undefined);

        const dto = new CoursesForDashboardDTO();
        dto.courses = [];
        inFlight.flush(dto);

        // The in-flight response must not write back into the cleared subject.
        expect(scoped['coursesForNotifications'].getValue()).toBeUndefined();
        subscription.unsubscribe();
    });

    it('should ignore in-flight findAllForNotifications responses after logout', () => {
        const subscription = scoped.findAllForNotifications().subscribe();
        const inFlight = scopedHttpMock.expectOne(`api/course/courses/for-notifications`);

        authState.next(undefined);

        inFlight.flush([{ id: 1 } as Course]);

        expect(scoped['coursesForNotifications'].getValue()).toBeUndefined();
        subscription.unsubscribe();
    });

    it('should ignore in-flight getAllCoursesWithQuizExercises responses after logout', () => {
        const subscription = scoped.getAllCoursesWithQuizExercises().subscribe();
        const inFlight = scopedHttpMock.expectOne(`api/course/courses/courses-with-quiz`);

        authState.next(undefined);

        inFlight.flush([{ id: 1 } as Course]);

        expect(scoped['coursesForNotifications'].getValue()).toBeUndefined();
        subscription.unsubscribe();
    });
});
