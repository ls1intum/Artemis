import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseManagementOverviewStatisticsDto } from 'app/core/course/manage/overview/course-management-overview-statistics-dto.model';
import { Course, CourseGroup } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType, ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { Organization } from 'app/core/shared/entities/organization.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { CourseForDashboardDTO, ParticipationResultDTO } from 'app/core/course/shared/entities/course-for-dashboard-dto';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { OnlineCourseDtoModel } from 'app/lti/shared/entities/online-course-dto.model';
import { CoursesForDashboardDTO } from 'app/core/course/shared/entities/courses-for-dashboard-dto';
import { provideHttpClient } from '@angular/common/http';
import { createSampleCourse } from 'test/helpers/sample/course-sample-data';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';

describe('Course Management Service', () => {
    setupTestBed({ zoneless: true });

    let courseManagementService: CourseManagementService;
    let accountService: AccountService;
    let lectureService: LectureService;
    let httpMock: HttpTestingController;
    let courseStorageService: CourseStorageService;
    let scoresStorageService: ScoresStorageService;
    let localStorageService: LocalStorageService;

    let isAtLeastTutorInCourseSpy: ReturnType<typeof vi.spyOn>;
    let isAtLeastEditorInCourseSpy: ReturnType<typeof vi.spyOn>;
    let isAtLeastInstructorInCourseSpy: ReturnType<typeof vi.spyOn>;
    let convertExercisesDateFromServerSpy: ReturnType<typeof vi.spyOn>;
    let convertDatesForLecturesFromServerSpy: ReturnType<typeof vi.spyOn>;
    let syncGroupsSpy: ReturnType<typeof vi.spyOn>;

    const resourceUrl = 'api/core/courses';

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

        isAtLeastTutorInCourseSpy = vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(false);
        isAtLeastEditorInCourseSpy = vi.spyOn(accountService, 'isAtLeastEditorInCourse').mockReturnValue(false);
        isAtLeastInstructorInCourseSpy = vi.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(false);
        syncGroupsSpy = vi.spyOn(accountService, 'syncGroups').mockImplementation(() => undefined);
        convertDatesForLecturesFromServerSpy = vi.spyOn(lectureService, 'convertLectureArrayDatesFromServer');
        ({ course, exercises } = createSampleCourse());

        courseForDashboard = new CourseForDashboardDTO();
        courseForDashboard.course = course;
        courseScores = new CourseScores(0, 0, 0, { absoluteScore: 0, relativeScore: 0, currentRelativeScore: 0, presentationScore: 0 });
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

        const req = httpMock.expectOne(`${resourceUrl}/for-lti-dashboard?clientId=${mockClientId}`);
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

    it('should find one course for dashboard', () => {
        returnedFromService = { ...courseForDashboard };
        courseStorageService
            .subscribeToCourseUpdates(course.id!)
            .pipe(take(1))
            .subscribe((updatedCourse) => {
                expect(updatedCourse).toEqual(course);
            });
        courseManagementService
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-dashboard`, returnedFromService, course, true);
    });

    it('should pass on an empty response body when fetching one course for dashboard and there is no response body sent from the server', () => {
        courseManagementService.findOneForDashboard(course.id!).subscribe((res) => expect(res.body).toBeNull());

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/for-dashboard` });
        req.flush(null);
    });

    it('should set the totalScores, the scoresPerExerciseType, and the participantScores in the scoresStorageService', () => {
        const setStoredTotalScoresSpy = vi.spyOn(scoresStorageService, 'setStoredTotalScores');
        const setStoredScoresPerExerciseTypeSpy = vi.spyOn(scoresStorageService, 'setStoredScoresPerExerciseType');
        const setParticipationResultsSpy = vi.spyOn(scoresStorageService, 'setStoredParticipationResults');
        courseManagementService
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe(() => {
                expect(setStoredTotalScoresSpy).toHaveBeenCalledWith(course.id!, courseScores);
                expect(setStoredScoresPerExerciseTypeSpy).toHaveBeenCalledWith(course.id!, scoresPerExerciseType);
                expect(setParticipationResultsSpy).toHaveBeenCalledWith(courseForDashboard.participationResults);
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
        const groups = ['student-group-name'];
        courseManagementService
            .registerForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(groups));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/enroll` });
        req.flush(groups);
        expect(syncGroupsSpy).toHaveBeenCalledWith(groups);
    });

    it('should unenroll from the course', () => {
        const groups = ['student-group-name'];
        courseManagementService
            .unenrollFromCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(groups));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/unenroll` });
        req.flush(groups);
        expect(syncGroupsSpy).toHaveBeenCalledWith(groups);
    });

    it('should get all courses with quiz exercises', () => {
        returnedFromService = [{ ...course }];
        courseManagementService
            .getAllCoursesWithQuizExercises()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/courses-with-quiz`, returnedFromService, course, true);
    });

    it('should get all courses together with user stats', () => {
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        courseManagementService
            .getWithUserStats(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/with-user-stats?testParam=testParamValue`, returnedFromService, course, true);
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

    it('should get all exercise details', () => {
        returnedFromService = [{ ...course }] as Course[];
        courseManagementService
            .getExercisesForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/exercises-for-management-overview?onlyActive=true`, returnedFromService, course);
    });

    it('should get all stats for overview', () => {
        const stats = [new CourseManagementOverviewStatisticsDto()];
        returnedFromService = [...stats];
        courseManagementService
            .getStatsForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/stats-for-management-overview?onlyActive=true` });
        req.flush(returnedFromService);
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
        const courseGroup = CourseGroup.STUDENTS;
        courseManagementService
            .getAllUsersInCourseGroup(course.id!, courseGroup)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(users));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/${courseGroup}` });
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
        const courseGroup = CourseGroup.STUDENTS;
        courseManagementService
            .addUserToCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
    });

    it('should remove user from course group', () => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        courseManagementService
            .removeUserFromCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
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
});
