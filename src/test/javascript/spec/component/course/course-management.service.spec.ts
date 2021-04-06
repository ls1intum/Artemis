import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { SERVER_API_URL } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
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
import * as chai from 'chai';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Course Management Service', () => {
    let injector: TestBed;
    let service: CourseManagementService;
    let accountService: AccountService;
    let exerciseService: ExerciseService;
    let lectureService: LectureService;
    let httpMock: HttpTestingController;
    let isAtLeastTutorInCourseStub: sinon.SinonStub;
    let isAtLeastInstructorInCourseStub: sinon.SinonStub;
    let convertExercisesDateFromServerStub: sinon.SinonStub;
    let convertDatesForLecturesFromServerStub: sinon.SinonStub;
    let syncGroupsStub: sinon.SinonStub;
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
        injector = getTestBed();
        service = injector.get(CourseManagementService);
        httpMock = injector.get(HttpTestingController);
        accountService = injector.get(AccountService);
        exerciseService = injector.get(ExerciseService);
        lectureService = injector.get(LectureService);

        isAtLeastTutorInCourseStub = sinon.stub(accountService, 'isAtLeastTutorInCourse');
        isAtLeastInstructorInCourseStub = sinon.stub(accountService, 'isAtLeastInstructorInCourse');
        syncGroupsStub = sinon.stub(accountService, 'syncGroups');
        convertExercisesDateFromServerStub = sinon.stub(exerciseService, 'convertExercisesDateFromServer');
        convertDatesForLecturesFromServerStub = sinon.stub(lectureService, 'convertDatesForLecturesFromServer');
        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        exercises = [new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined), new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined)];
        course.exercises = exercises;
        returnedFromService = { ...course };
        participations = [new StudentParticipation()];
    });

    const expectDateConversionToBeCalled = (courseForConversion: Course) => {
        expect(convertExercisesDateFromServerStub).to.have.been.calledWith(courseForConversion.exercises);
        expect(convertDatesForLecturesFromServerStub).to.have.been.calledWith(courseForConversion.lectures);
    };

    const expectAccessRightsToBeCalled = () => {
        expect(isAtLeastTutorInCourseStub).to.have.been.called;
        expect(isAtLeastInstructorInCourseStub).to.have.been.called;
    };

    const requestAndExpectDateConversion = (method: string, url: string, flushedObject: any = returnedFromService, courseToCheck: Course, checkAccessRights?: boolean) => {
        const req = httpMock.expectOne({ method, url });
        req.flush(flushedObject);
        expectDateConversionToBeCalled(courseToCheck);
        if (checkAccessRights) {
            expectAccessRightsToBeCalled();
        }
    };

    it('should create course', async () => {
        delete course.id;
        service
            .create({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        req.flush(returnedFromService);
    });

    it('should update course', async () => {
        service
            .update({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));

        const req = httpMock.expectOne({ method: 'PUT', url: resourceUrl });
        req.flush(returnedFromService);
    });

    it('should find the course', async () => {
        service
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}`, returnedFromService, course);
    });

    it('should get title of the course', async () => {
        returnedFromService = course.title!;
        service
            .getTitle(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course.title));

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/title` });
        req.flush(returnedFromService);
    });

    it('should find course with exercises', async () => {
        service
            .findWithExercises(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises`, returnedFromService, course);
    });

    it('should find course with exercises and participations', async () => {
        service
            .findWithExercisesAndParticipations(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises-and-relevant-participations`, returnedFromService, course);
    });

    it('should find course with organizations', async () => {
        course.organizations = [new Organization()];
        returnedFromService = { ...course };
        service
            .findWithOrganizations(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-organizations`, returnedFromService, course);
    });

    it('should find all courses for dashboard', async () => {
        returnedFromService = [{ ...course }];
        service
            .findAllForDashboard()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/for-dashboard`, returnedFromService, course);
    });

    it('should find one course for dashboard', async () => {
        service
            .getCourseUpdates(course.id!)
            .pipe(take(1))
            .subscribe((updatedCourse) => {
                expect(updatedCourse).to.eq(course);
            });
        service
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-dashboard`, returnedFromService, course, true);
    });

    it('should find participations for the course', async () => {
        returnedFromService = [...participations];
        service
            .findAllParticipationsWithResults(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res).to.eq(participations));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/participations` });
        req.flush(returnedFromService);
    });

    it('should find reults for the course', async () => {
        service.findAllResultsOfCourseForExerciseAndCurrentUser(course.id!).subscribe((res) => expect(res).to.eq(course));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/results` });
        req.flush(returnedFromService);
    });

    it('should find all courses to register', async () => {
        returnedFromService = [{ ...course }];
        service
            .findAllToRegister()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/to-register`, returnedFromService, course);
    });

    it('should find course with interesting exercises', async () => {
        service
            .getCourseWithInterestingExercisesForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-assessment-dashboard`, returnedFromService, course);
    });

    it('should get stats of course', async () => {
        const stats = new StatsForDashboard();
        returnedFromService = { ...stats };
        service
            .getStatsForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/stats-for-assessment-dashboard` });
        req.flush(returnedFromService);
    });

    it('should register for the course', async () => {
        const user = new User(1, 'name');
        service
            .registerForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(user));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/register` });
        req.flush(user);
        expect(syncGroupsStub).to.have.been.calledWith(user);
    });

    it('should get all courses', async () => {
        returnedFromService = [{ ...course }];
        const params = { testParam: 'testParamValue' };
        service
            .getAll(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        requestAndExpectDateConversion('GET', `${resourceUrl}?testParam=testParamValue`, returnedFromService, course, true);
    });

    it('should get all courses with quiz exercises', async () => {
        service.getCoursesForNotifications().subscribe((updatedCourse) => {
            expect(updatedCourse).to.eq(course);
        });
        returnedFromService = [{ ...course }];
        service
            .getAllCoursesWithQuizExercises()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/courses-with-quiz`, returnedFromService, course, true);
    });

    it('should get all courses together with user stats', async () => {
        service
            .getCoursesForNotifications()
            .pipe(take(1))
            .subscribe((updatedCourse) => {
                expect(updatedCourse).to.eq(course);
            });
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        service
            .getWithUserStats(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/with-user-stats?testParam=testParamValue`, returnedFromService, course, true);
    });

    it('should get all courses for overview', async () => {
        service.getCoursesForNotifications().subscribe((updatedCourse) => {
            expect(updatedCourse).to.eq(course);
        });
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        service
            .getCourseOverview(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/course-management-overview?testParam=testParamValue` });
        req.flush(returnedFromService);
        expectAccessRightsToBeCalled();
    });

    it('should delete a course ', async () => {
        service
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.not.exist);
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({});
    });

    it('should get stats of instructor', async () => {
        const stats = new StatsForDashboard();
        returnedFromService = { ...stats };
        service
            .getStatsForInstructors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/stats-for-instructor-dashboard` });
        req.flush(returnedFromService);
    });

    it('should get all exercise details ', async () => {
        returnedFromService = [{ ...course }];
        service
            .getExercisesForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([course]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/exercises-for-management-overview?onlyActive=true`, returnedFromService, course);
    });

    it('should get all stats for overview', async () => {
        const stats = [new CourseManagementOverviewStatisticsDto()];
        returnedFromService = [...stats];
        service
            .getStatsForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq([stats]));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/stats-for-management-overview?onlyActive=true` });
        req.flush(returnedFromService);
    });

    it('should find all categories of course', async () => {
        const categories = ['category1', 'category2'];
        returnedFromService = [...categories];
        service
            .findAllCategoriesOfCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(categories));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/categories` });
        req.flush(returnedFromService);
    });

    it('should find all users of course group', async () => {
        const users = [new User(1, 'user1'), new User(2, 'user2')];
        returnedFromService = [...users];
        const courseGroup = CourseGroup.STUDENTS;
        service
            .getAllUsersInCourseGroup(course.id!, courseGroup)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.eq(users));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/${courseGroup}` });
        req.flush(returnedFromService);
    });

    it('should find all users of course group', async () => {
        const expectedBlob = new Blob(['abc', 'cfe']);
        service.downloadCourseArchive(course.id!).subscribe((resp) => {
            expect(resp.body).to.equal(expectedBlob);
        });
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/download-archive` });
        req.flush(expectedBlob);
    });

    it('should archive the course', async () => {
        service.archiveCourse(course.id!).subscribe((res) => expect(res).to.eq(course));
        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/${course.id}/archive` });
        req.flush(returnedFromService);
    });

    it('should clean up the course', async () => {
        service.cleanupCourse(course.id!).subscribe((res) => expect(res).to.eq(course));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/cleanup` });
        req.flush(returnedFromService);
    });

    it('should find all locked submissions of course', () => {
        const submission = new ModelingSubmission();
        const submissions = [submission];
        returnedFromService = [...submissions];
        service.findAllLockedSubmissionsOfCourse(course.id!).subscribe((res) => expect(res).to.eq(course));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/lockedSubmissions` });
        req.flush(returnedFromService);
    });

    it('should add user to course group', () => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        service
            .addUserToCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.not.exist);
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
    });

    it('should remove user from course group', () => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        service
            .removeUserFromCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.not.exist);
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });
});
