import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { Course, CourseGroup } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { Organization } from 'app/entities/organization.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LectureService } from 'app/lecture/lecture.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('Course Management Service', () => {
    let courseManagementService: CourseManagementService;
    let accountService: AccountService;
    let lectureService: LectureService;
    let httpMock: HttpTestingController;
    let isAtLeastTutorInCourseSpy: jest.SpyInstance;
    let isAtLeastEditorInCourseSpy: jest.SpyInstance;
    let isAtLeastInstructorInCourseSpy: jest.SpyInstance;
    let convertExercisesDateFromServerSpy: jest.SpyInstance;
    let convertDatesForLecturesFromServerSpy: jest.SpyInstance;
    let syncGroupsSpy: jest.SpyInstance;
    const resourceUrl = SERVER_API_URL + 'api/courses';
    let course: Course;
    let exercises: Exercise[];
    let returnedFromService: any;
    let participations: StudentParticipation[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        courseManagementService = TestBed.inject(CourseManagementService);
        httpMock = TestBed.inject(HttpTestingController);
        accountService = TestBed.inject(AccountService);
        lectureService = TestBed.inject(LectureService);

        isAtLeastTutorInCourseSpy = jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(false);
        isAtLeastEditorInCourseSpy = jest.spyOn(accountService, 'isAtLeastEditorInCourse').mockReturnValue(false);
        isAtLeastInstructorInCourseSpy = jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(false);
        syncGroupsSpy = jest.spyOn(accountService, 'syncGroups').mockImplementation();
        convertDatesForLecturesFromServerSpy = jest.spyOn(lectureService, 'convertLectureArrayDatesFromServer');
        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        exercises = [new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined), new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined)];
        course.exercises = exercises;
        course.lectures = undefined;
        course.startDate = undefined;
        course.endDate = undefined;
        course.learningGoals = [];
        course.prerequisites = [];
        returnedFromService = { ...course } as Course;
        participations = [new StudentParticipation()];
        convertExercisesDateFromServerSpy = jest.spyOn(ExerciseService, 'convertExercisesDateFromServer').mockReturnValue(exercises);
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

    it('should create course', fakeAsync(() => {
        delete course.id;

        courseManagementService
            .create({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({ ...course, id: 1234 }));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update course', fakeAsync(() => {
        courseManagementService
            .update({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));

        const req = httpMock.expectOne({ method: 'PUT', url: resourceUrl });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find the course', fakeAsync(() => {
        courseManagementService
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}`, returnedFromService, course);
        tick();
    }));

    it('should set accessRights with by using the AccountService', fakeAsync(() => {
        courseManagementService
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}`, returnedFromService, course, true);
        tick();
    }));

    it('should find course with exercises', fakeAsync(() => {
        courseManagementService
            .findWithExercises(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises`, returnedFromService, course);
        tick();
    }));

    it('should find course with organizations', fakeAsync(() => {
        course.organizations = [new Organization()];
        returnedFromService = { ...course };
        courseManagementService
            .findWithOrganizations(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-organizations`, returnedFromService, course);
        tick();
    }));

    it('should find all courses for dashboard', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        courseManagementService
            .findAllForDashboard()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/for-dashboard`, returnedFromService, course);
        tick();
    }));

    it('should find one course for dashboard', fakeAsync(() => {
        courseManagementService
            .getCourseUpdates(course.id!)
            .pipe(take(1))
            .subscribe((updatedCourse) => {
                expect(updatedCourse).toEqual(course);
            });
        courseManagementService
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-dashboard`, returnedFromService, course, true);
        tick();
    }));

    it('should find participations for the course', fakeAsync(() => {
        returnedFromService = [...participations];
        courseManagementService
            .findAllParticipationsWithResults(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(participations));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/participations` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find results for the course', fakeAsync(() => {
        courseManagementService.findAllResultsOfCourseForExerciseAndCurrentUser(course.id!).subscribe((res) => expect(res).toEqual(course));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/results` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all courses to register', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        courseManagementService
            .findAllToRegister()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/for-registration`, returnedFromService, course);
        tick();
    }));

    it('should find course with interesting exercises', fakeAsync(() => {
        courseManagementService
            .getCourseWithInterestingExercisesForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-assessment-dashboard`, returnedFromService, course);
        tick();
    }));

    it('should get stats of course', fakeAsync(() => {
        const stats = new StatsForDashboard();
        returnedFromService = { ...stats };
        courseManagementService
            .getStatsForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/stats-for-assessment-dashboard` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should register for the course', fakeAsync(() => {
        const user = new User(1, 'name');
        courseManagementService
            .registerForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(user));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/register` });
        req.flush(user);
        expect(syncGroupsSpy).toHaveBeenCalledWith(user);
        tick();
    }));

    it('should get all courses', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        const params = { testParam: 'testParamValue' };
        courseManagementService
            .getAll(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}?testParam=testParamValue`, returnedFromService, course, true);
        tick();
    }));

    it('should get all courses with quiz exercises', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        courseManagementService
            .getAllCoursesWithQuizExercises()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/courses-with-quiz`, returnedFromService, course, true);
        tick();
    }));

    it('should get all courses together with user stats', fakeAsync(() => {
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        courseManagementService
            .getWithUserStats(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/with-user-stats?testParam=testParamValue`, returnedFromService, course, true);
        tick();
    }));

    it('should get all courses for overview', fakeAsync(() => {
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        courseManagementService
            .getCourseOverview(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/course-management-overview?testParam=testParamValue` });
        req.flush(returnedFromService);
        expectAccessRightsToBeCalled(1, 1, 1);
        tick();
    }));

    it('should delete a course', fakeAsync(() => {
        courseManagementService
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({});
        tick();
    }));

    it('should get all exercise details', fakeAsync(() => {
        returnedFromService = [{ ...course }] as Course[];
        courseManagementService
            .getExercisesForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/exercises-for-management-overview?onlyActive=true`, returnedFromService, course);
        tick();
    }));

    it('should get all stats for overview', fakeAsync(() => {
        const stats = [new CourseManagementOverviewStatisticsDto()];
        returnedFromService = [...stats];
        courseManagementService
            .getStatsForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/stats-for-management-overview?onlyActive=true` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all categories of course', fakeAsync(() => {
        const categories = ['category1', 'category2'];
        returnedFromService = [...categories];
        courseManagementService
            .findAllCategoriesOfCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(categories));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/categories` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all users of course group', fakeAsync(() => {
        const users = [new User(1, 'user1'), new User(2, 'user2')];
        returnedFromService = [...users];
        const courseGroup = CourseGroup.STUDENTS;
        courseManagementService
            .getAllUsersInCourseGroup(course.id!, courseGroup)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(users));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/${courseGroup}` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should download course archive', fakeAsync(() => {
        const expectedBlob = new Blob(['abc', 'cfe']);
        courseManagementService.downloadCourseArchive(course.id!).subscribe((resp) => {
            expect(resp.body).toEqual(expectedBlob);
        });
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/download-archive` });
        req.flush(expectedBlob);
        tick();
    }));

    it('should archive the course', fakeAsync(() => {
        courseManagementService.archiveCourse(course.id!).subscribe((res) => expect(res.body).toEqual(course));
        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/${course.id}/archive` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should clean up the course', fakeAsync(() => {
        courseManagementService.cleanupCourse(course.id!).subscribe((res) => expect(res.body).toEqual(course));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/cleanup` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all locked submissions of course', fakeAsync(() => {
        const submission = new ModelingSubmission();
        const submissions = [submission];
        returnedFromService = [...submissions];
        courseManagementService.findAllLockedSubmissionsOfCourse(course.id!).subscribe((res) => expect(res.body).toEqual(submissions));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/lockedSubmissions` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should add user to course group', fakeAsync(() => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        courseManagementService
            .addUserToCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
        tick();
    }));

    it('should remove user from course group', fakeAsync(() => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        courseManagementService
            .removeUserFromCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
        tick();
    }));

    it('should return lifetime overview data', fakeAsync(() => {
        const stats = [34, 23, 45, 67, 89, 201, 67, 890, 1359];
        courseManagementService
            .getStatisticsForLifetimeOverview(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/statistics-lifetime-overview` });
        req.flush(stats);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });
});
